package com.redhat.jenkins.plugins.ci.pipeline;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker.SubscribedCallback;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingWorker;

import hudson.model.Result;

public class CIMessageSubscriberBlockTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private final RabbitMQMessagingWorker worker = mock(RabbitMQMessagingWorker.class);
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean workerExited = new AtomicBoolean();

    private WorkflowJob job(String body) throws Exception {
        RabbitMQMessagingProvider provider = mock(RabbitMQMessagingProvider.class);
        when(provider.getName()).thenReturn("test");
        when(provider.createWorker(any(), anyString())).thenReturn(worker);
        GlobalCIConfiguration.get().setConfigs(Collections.singletonList(provider));
        when(worker.getLastError()).thenAnswer(call -> error.get());
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("node { def reply = withCIMessage(providerData: "
                + "[$class: 'RabbitMQSubscriberProviderData', name: 'test']) { " + body + " }; echo 'REPLY=' + reply }",
                true));
        return job;
    }

    private void readyWorker() throws Exception {
        when(worker.waitForMessage(any(), any(), any(), any(), any())).thenAnswer(call -> {
            SubscribedCallback ready = call.getArgument(4);
            try {
                ready.run().get();
                return "{\"result\":\"ok\"}";
            } catch (Exception failure) {
                error.set(failure);
                return null;
            } finally {
                workerExited.set(true);
            }
        });
    }

    @Test
    public void executesRealPipelineBodyAndReturnsResponseAfterBodyCompletes() throws Exception {
        WorkflowJob job = job("echo 'BODY_STARTED'; sleep time: 1, unit: 'MILLISECONDS'; echo 'BODY_DONE'");
        readyWorker();
        WorkflowRun build = j.buildAndAssertSuccess(job);
        j.assertLogContains("BODY_STARTED", build);
        j.assertLogContains("BODY_DONE", build);
        j.assertLogContains("REPLY={\"result\":\"ok\"}", build);
    }

    @Test
    public void bodyFailureIsReportedInsteadOfTimeout() throws Exception {
        WorkflowJob job = job("error 'BODY_FAILED'");
        readyWorker();
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("BODY_FAILED", build);
        j.assertLogNotContains("Timeout waiting for message!", build);
    }

    @Test
    public void receiverFailureCancelsRunningBody() throws Exception {
        WorkflowJob job = job("echo 'BODY_WAITING'; sleep time: 10, unit: 'MINUTES'; echo 'BODY_MUST_NOT_FINISH'");
        when(worker.waitForMessage(any(), any(), any(), any(), any())).thenAnswer(call -> {
            ((SubscribedCallback) call.getArgument(4)).run();
            j.waitForMessage("BODY_WAITING", (WorkflowRun) call.getArgument(0));
            error.set(new IOException("receiver disconnected"));
            return null;
        });
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("receiver disconnected", build);
        j.assertLogNotContains("BODY_MUST_NOT_FINISH", build);
    }

    @Test
    public void setupFailureNeverExecutesBody() throws Exception {
        WorkflowJob job = job("echo 'BODY_MUST_NOT_RUN'");
        error.set(new IOException("binding denied"));
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogNotContains("BODY_MUST_NOT_RUN", build);
        j.assertLogContains("binding denied", build);
    }

    @Test
    public void timeoutIsReportedAfterBody() throws Exception {
        WorkflowJob job = job("echo 'BODY_DONE'");
        when(worker.waitForMessage(any(), any(), any(), any(), any())).thenAnswer(call -> {
            ((SubscribedCallback) call.getArgument(4)).run().get();
            return null;
        });
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("BODY_DONE", build);
        j.assertLogContains("Timeout waiting for message!", build);
    }

    @Test
    public void existingCallWithoutBodyStillUsesWaitOnlyExecution() throws Exception {
        WorkflowJob job = job("");
        job.setDefinition(new CpsFlowDefinition("node { def reply = waitForCIMessage(providerData: "
                + "[$class: 'RabbitMQSubscriberProviderData', name: 'test']); echo 'REPLY=' + reply }", true));
        when(worker.waitForMessage(any(), any(), any(), any())).thenReturn("{\"legacy\":true}");
        WorkflowRun build = j.buildAndAssertSuccess(job);
        j.assertLogContains("REPLY={\"legacy\":true}", build);
    }

    @Test
    public void rejectsUnsupportedProviderDataBeforeStartingBody() throws Exception {
        WorkflowJob job = job("");
        job.setDefinition(new CpsFlowDefinition(
                "node { withCIMessage(providerData: "
                        + "[$class: 'ActiveMQSubscriberProviderData', name: 'test']) { echo 'BODY_MUST_NOT_RUN' } }",
                true));
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("requires matching ActiveMQ, RabbitMQ, or Kafka subscriber data and provider", build);
        j.assertLogNotContains("BODY_MUST_NOT_RUN", build);
    }

    @Test
    public void activeMQAndKafkaExecuteTheSamePipelineBlockContract() throws Exception {
        JMSMessagingProvider[] providers = { mock(ActiveMqMessagingProvider.class),
                mock(KafkaMessagingProvider.class) };
        JMSMessagingWorker[] workers = { mock(ActiveMqMessagingWorker.class), mock(KafkaMessagingWorker.class) };
        String[] dataClasses = { "ActiveMQSubscriberProviderData", "KafkaSubscriberProviderData" };
        for (int i = 0; i < providers.length; i++) {
            when(providers[i].getName()).thenReturn("test");
            when(providers[i].createWorker(any(), anyString())).thenReturn(workers[i]);
            when(workers[i].waitForMessage(any(), any(), any(), any(), any())).thenAnswer(call -> {
                ((SubscribedCallback) call.getArgument(4)).run().get();
                return "{\"result\":\"ok\"}";
            });
            GlobalCIConfiguration.get().setConfigs(Collections.singletonList(providers[i]));
            WorkflowJob job = j.createProject(WorkflowJob.class);
            job.setDefinition(new CpsFlowDefinition("node { def reply = withCIMessage(providerData: " + "[$class: '"
                    + dataClasses[i] + "', name: 'test']) { echo 'BODY_DONE' }; " + "echo 'REPLY=' + reply }", true));
            WorkflowRun build = j.buildAndAssertSuccess(job);
            j.assertLogContains("BODY_DONE", build);
            j.assertLogContains("REPLY={\"result\":\"ok\"}", build);
        }
    }

    @Test
    public void abortCancelsBodyAndSubscription() throws Exception {
        WorkflowJob job = job("echo 'BODY_WAITING'; sleep time: 10, unit: 'MINUTES'");
        readyWorker();
        WorkflowRun build = job.scheduleBuild2(0).waitForStart();
        j.waitForMessage("BODY_WAITING", build);
        build.doStop();
        j.waitForCompletion(build);
        j.assertBuildStatus(Result.ABORTED, build);
        assertTrue("The subscription worker must exit on cancellation", workerExited.get());
    }
}
