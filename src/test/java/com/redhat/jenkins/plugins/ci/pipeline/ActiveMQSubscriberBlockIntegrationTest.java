package com.redhat.jenkins.plugins.ci.pipeline;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Collections;
import java.util.UUID;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.activemq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.DefaultTopicProvider;

import hudson.model.Result;

/** Run against an isolated broker with -Dactivemq.test.broker=tcp://HOST:PORT. */
@WithJenkins
public class ActiveMQSubscriberBlockIntegrationTest {
    private JenkinsRule j;

    private String broker;
    private String destination;

    @BeforeEach
    public void setup(JenkinsRule j) throws Exception {
        this.j = j;
        broker = System.getProperty("activemq.test.broker");
        assumeTrue(broker != null, "Set activemq.test.broker to run real-broker tests");
        String user = System.getProperty("activemq.test.user", "jms-test");
        String password = System.getProperty("activemq.test.password", "jms-test");
        destination = "ready-test-" + UUID.randomUUID();
        SystemCredentialsProvider.getInstance().getCredentials().add(new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, "broker-test", "Isolated test broker", user, password));
    }

    private void configure(boolean useQueues) {
        GlobalCIConfiguration.get().setConfigs(Collections.singletonList(new ActiveMqMessagingProvider("test", broker,
                useQueues, destination, new DefaultTopicProvider(), new UsernameAuthenticationMethod("broker-test"))));
    }

    private WorkflowJob job(String body) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("node { def reply = withCIMessage(providerData: "
                + "[$class: 'ActiveMQSubscriberProviderData', name: 'test', timeout: 1]) { " + body
                + " }; echo 'REPLY=' + reply }", true));
        return job;
    }

    private static final String SEND = "sendCIMessage(providerData: [$class: 'ActiveMQPublisherProviderData', "
            + "name: 'test', messageContent: '{\"result\":\"ok\"}', failOnError: true]); ";

    @Test
    public void receivesSingleImmediateTopicReplyWithoutSleeps() throws Exception {
        configure(false);
        WorkflowRun build = j.buildAndAssertSuccess(job(SEND + "echo 'BODY_DONE'"));
        j.assertLogContains("ActiveMQ subscription ready: destination=" + destination, build);
        j.assertLogContains("BODY_DONE", build);
        j.assertLogContains("REPLY={\"result\":\"ok\"}", build);
    }

    @Test
    public void receivesSingleImmediateQueueReplyWithoutSleeps() throws Exception {
        configure(true);
        WorkflowRun build = j.buildAndAssertSuccess(job(SEND + "echo 'BODY_DONE'"));
        j.assertLogContains("ActiveMQ subscription ready: destination=" + destination, build);
        j.assertLogContains("BODY_DONE", build);
        j.assertLogContains("REPLY={\"result\":\"ok\"}", build);
    }

    @Test
    public void bodyFailureIsPreservedWithARealConsumer() throws Exception {
        configure(false);
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job("error 'REQUEST_FAILED'").scheduleBuild2(0));
        j.assertLogContains("ActiveMQ subscription ready:", build);
        j.assertLogContains("REQUEST_FAILED", build);
        j.assertLogNotContains("Timed out waiting for message!", build);
    }

    @Test
    public void abortStopsTheBodyAndReceiver() throws Exception {
        configure(false);
        WorkflowRun build = job("echo 'BODY_WAITING'; sleep time: 10, unit: 'MINUTES'").scheduleBuild2(0)
                .waitForStart();
        j.waitForMessage("BODY_WAITING", build);
        build.doStop();
        j.waitForCompletion(build);
        j.assertBuildStatus(Result.ABORTED, build);
    }
}
