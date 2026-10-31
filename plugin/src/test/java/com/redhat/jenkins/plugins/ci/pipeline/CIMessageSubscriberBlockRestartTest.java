package com.redhat.jenkins.plugins.ci.pipeline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingWorker.SubscribedCallback;

import hudson.model.Result;

public class CIMessageSubscriberBlockRestartTest {
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void restartFailsExplicitlyInsteadOfHangingOrReplayingRequest() {
        story.then(j -> {
            RabbitMQMessagingProvider provider = mock(RabbitMQMessagingProvider.class);
            RabbitMQMessagingWorker worker = mock(RabbitMQMessagingWorker.class);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            when(provider.getName()).thenReturn("test");
            when(provider.createWorker(any(), anyString())).thenReturn(worker);
            when(worker.getLastError()).thenAnswer(call -> failure.get());
            when(worker.waitForMessage(any(), any(), any(), any(), any())).thenAnswer(call -> {
                try {
                    ((SubscribedCallback) call.getArgument(4)).run();
                } catch (Exception e) {
                    failure.set(e);
                }
                return null;
            });
            GlobalCIConfiguration.get().setConfigs(Collections.singletonList(provider));
            WorkflowJob job = j.createProject(WorkflowJob.class, "request-reply");
            job.setDefinition(new CpsFlowDefinition("node { withCIMessage(providerData: "
                    + "[$class: 'RabbitMQSubscriberProviderData', name: 'test']) { "
                    + "echo 'BODY_STARTED'; sleep time: 10, unit: 'MINUTES' } }", true));
            WorkflowRun build = job.scheduleBuild2(0).waitForStart();
            j.waitForMessage("BODY_STARTED", build);
        });
        story.then(j -> {
            WorkflowRun build = j.jenkins.getItemByFullName("request-reply", WorkflowJob.class).getBuildByNumber(1);
            j.waitForCompletion(build);
            j.assertBuildStatus(Result.FAILURE, build);
            j.assertLogContains("cannot resume after a Jenkins restart", build);
        });
    }
}
