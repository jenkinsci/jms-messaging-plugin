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
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQSubscriberProviderData;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Runs a Pipeline body only after the messaging consumer is ready. Deliveries remain unacknowledged until the body
 * completes and the response is collected.
 */
final class CIMessageBlockExecution extends StepExecution {
    private static final long serialVersionUID = 1L;

    private final ProviderData providerData;
    private final AtomicBoolean completed = new AtomicBoolean();
    private BodyExecution body;
    private transient volatile Future<?> task;
    private transient CompletableFuture<Void> bodyCompletion;
    private transient volatile JMSMessagingWorker worker;

    CIMessageBlockExecution(ProviderData providerData, StepContext context) {
        super(context);
        this.providerData = providerData;
    }

    @Override
    public boolean start() throws Exception {
        if (providerData == null) {
            throw new AbortException("withCIMessage requires subscriber provider data.");
        }
        JMSMessagingProvider provider = GlobalCIConfiguration.get().getProvider(providerData.getName());
        boolean supported = (provider instanceof RabbitMQMessagingProvider
                && providerData instanceof RabbitMQSubscriberProviderData)
                || (provider instanceof ActiveMqMessagingProvider
                        && providerData instanceof ActiveMQSubscriberProviderData)
                || (provider instanceof KafkaMessagingProvider && providerData instanceof KafkaSubscriberProviderData);
        if (!supported) {
            throw new AbortException(
                    "withCIMessage requires matching ActiveMQ, RabbitMQ, or Kafka subscriber data and provider.");
        }
        bodyCompletion = new CompletableFuture<>();
        worker = provider.createWorker(providerData, getContext().get(Run.class).getParent().getFullName());
        // A subscription can outlive the body; do not occupy Jenkins' timer threads.
        task = Computer.threadPoolForRemoting.submit(this::receive);
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
                        return bodyCompletion;
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

    private synchronized void complete(String message, Throwable failure) {
        if (completed.compareAndSet(false, true)) {
            if (failure == null) {
                getContext().onSuccess(message);
            } else {
                if (body != null) {
                    body.cancel(failure);
                }
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
                    worker.cancelWait();
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
            stop(new AbortException("The withCIMessage subscription cannot resume after a Jenkins restart. "
                    + "Retry the complete request/response block with a new correlation ID."));
        } catch (Exception failure) {
            getContext().onFailure(failure);
        }
    }

    private static final class Callback extends BodyExecutionCallback {
        private static final long serialVersionUID = 1L;
        private final CIMessageBlockExecution execution;

        Callback(CIMessageBlockExecution execution) {
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
