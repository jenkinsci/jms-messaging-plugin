package com.redhat.jenkins.plugins.ci.pipeline;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingWorker;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQSubscriberProviderData;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.util.Timer;

/**
 * Runs a Pipeline body only after the RabbitMQ consumer is ready. Deliveries remain unacknowledged until the body
 * completes and the response is collected.
 */
final class RabbitMQSubscriberBlockExecution extends StepExecution {
    private static final long serialVersionUID = 1L;

    private final ProviderData providerData;
    private final AtomicBoolean completed = new AtomicBoolean();
    private BodyExecution body;
    private transient volatile Future<?> task;
    private transient CompletableFuture<Void> bodyCompletion;
    private transient volatile RabbitMQMessagingWorker worker;

    RabbitMQSubscriberBlockExecution(ProviderData providerData, StepContext context) {
        super(context);
        this.providerData = providerData;
    }

    @Override
    public boolean start() throws Exception {
        if (!(providerData instanceof RabbitMQSubscriberProviderData)) {
            throw new AbortException("withCIMessage supports RabbitMQ subscribers only.");
        }
        JMSMessagingProvider provider = GlobalCIConfiguration.get().getProvider(providerData.getName());
        if (!(provider instanceof RabbitMQMessagingProvider)) {
            throw new AbortException("withCIMessage requires a configured RabbitMQ provider.");
        }
        bodyCompletion = new CompletableFuture<>();
        worker = (RabbitMQMessagingWorker) provider.createWorker(providerData,
                getContext().get(Run.class).getParent().getFullName());
        task = Timer.get().submit(this::receive);
        // A cancellation can race with submitting the background task.
        if (completed.get()) {
            task.cancel(true);
        }
        return false;
    }

    private void receive() {
        try {
            StepContext context = getContext();
            String message = worker.waitForMessage(context.get(Run.class), context.get(TaskListener.class),
                    providerData, context.get(FilePath.class), () -> {
                        synchronized (this) {
                            if (completed.get()) {
                                throw new InterruptedException("Subscription was cancelled before starting its body.");
                            }
                            body = context.newBodyInvoker().withCallback(new Callback(this)).start();
                        }
                        bodyCompletion.get();
                    });
            if (message == null) {
                Throwable cause = worker.getLastError();
                if (cause instanceof ExecutionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                complete(null, cause != null ? cause : new AbortException("Timeout waiting for message!"));
            } else {
                complete(message, null);
            }
        } catch (Exception failure) {
            complete(null, failure);
        }
    }

    private void complete(String message, Throwable failure) {
        if (completed.compareAndSet(false, true)) {
            if (failure == null) {
                getContext().onSuccess(message);
            } else {
                getContext().onFailure(failure);
            }
        }
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        synchronized (this) {
            if (completed.compareAndSet(false, true)) {
                if (body != null) {
                    body.cancel(cause);
                }
                if (worker != null) {
                    worker.prepareForInterrupt();
                }
                if (task != null) {
                    task.cancel(true);
                }
                getContext().onFailure(cause);
            }
        }
    }

    @Override
    public void onResume() {
        try {
            stop(new AbortException("The withCIMessage RabbitMQ subscription cannot resume after a Jenkins restart. "
                    + "Retry the complete request/response block with a new correlation ID."));
        } catch (Exception failure) {
            getContext().onFailure(failure);
        }
    }

    private static final class Callback extends BodyExecutionCallback {
        private static final long serialVersionUID = 1L;
        private final RabbitMQSubscriberBlockExecution execution;

        Callback(RabbitMQSubscriberBlockExecution execution) {
            this.execution = execution;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            if (execution.bodyCompletion != null) {
                execution.bodyCompletion.complete(null);
            }
        }

        @Override
        public void onFailure(StepContext context, Throwable failure) {
            if (execution.bodyCompletion != null) {
                execution.bodyCompletion.completeExceptionally(failure);
            }
        }
    }
}
