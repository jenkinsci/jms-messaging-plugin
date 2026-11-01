package com.redhat.jenkins.plugins.ci.pipeline;

import static org.junit.Assume.assumeTrue;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.kafka.NoneAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;

import hudson.model.Result;

/** Run against an isolated broker with -Dkafka.test.bootstrap=HOST:PORT. */
public class KafkaSubscriberBlockIntegrationTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private Admin admin;
    private String topic;

    @Before
    public void setup() throws Exception {
        String bootstrap = System.getProperty("kafka.test.bootstrap");
        assumeTrue("Set kafka.test.bootstrap to run real-broker tests", bootstrap != null);
        admin = Admin.create(Collections.singletonMap("bootstrap.servers", bootstrap));
        topic = "ready-test-" + UUID.randomUUID();
        admin.createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1))).all().get(30,
                TimeUnit.SECONDS);
        GlobalCIConfiguration.get().setConfigs(Collections.singletonList(new KafkaMessagingProvider("test", topic,
                "bootstrap.servers=" + bootstrap, "bootstrap.servers=" + bootstrap + "\nauto.offset.reset=latest",
                new NoneAuthenticationMethod())));
    }

    @After
    public void cleanup() throws Exception {
        if (admin != null) {
            try {
                if (topic != null) {
                    admin.deleteTopics(Collections.singletonList(topic)).all().get(30, TimeUnit.SECONDS);
                }
            } finally {
                admin.close();
            }
        }
    }

    private WorkflowJob job(String body) throws Exception {
        return job(body, "");
    }

    private WorkflowJob job(String body, String properties) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("node { def reply = withCIMessage(providerData: "
                + "[$class: 'KafkaSubscriberProviderData', name: 'test', timeout: 1, properties: '" + properties
                + "']) { " + body + " }; echo 'REPLY=' + reply }", true));
        return job;
    }

    @Test
    public void receivesSingleImmediateReplyWithLatestOffsetResetAndNoSleeps() throws Exception {
        WorkflowJob job = job("sendCIMessage(providerData: [$class: 'KafkaPublisherProviderData', "
                + "name: 'test', messageContent: '{\"result\":\"ok\"}', failOnError: true]); echo 'BODY_DONE'");
        WorkflowRun build = j.buildAndAssertSuccess(job);
        j.assertLogContains("Kafka subscription ready:", build);
        j.assertLogContains("BODY_DONE", build);
        j.assertLogContains("REPLY={\"result\":\"ok\"}", build);
    }

    @Test
    public void bodyFailureIsPreservedWithARealConsumer() throws Exception {
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job("error 'REQUEST_FAILED'").scheduleBuild2(0));
        j.assertLogContains("Kafka subscription ready:", build);
        j.assertLogContains("REQUEST_FAILED", build);
        j.assertLogNotContains("Timeout waiting for message!", build);
    }

    @Test
    public void keepsPollingDuringBodyBeyondMaxPollInterval() throws Exception {
        WorkflowJob job = job("sendCIMessage(providerData: [$class: 'KafkaPublisherProviderData', "
                + "name: 'test', messageContent: '{\"result\":\"ok\"}', failOnError: true]); "
                + "sleep time: 2, unit: 'SECONDS'; echo 'BODY_DONE'", "max.poll.interval.ms=1000");
        WorkflowRun build = j.buildAndAssertSuccess(job);
        j.assertLogContains("BODY_DONE", build);
        j.assertLogContains("REPLY={\"result\":\"ok\"}", build);
    }

    @Test
    public void abortCancelsBodyAndRealKafkaConsumer() throws Exception {
        WorkflowRun build = job("echo 'BODY_WAITING'; sleep time: 10, unit: 'MINUTES'").scheduleBuild2(0)
                .waitForStart();
        j.waitForMessage("BODY_WAITING", build);
        build.doStop();
        j.waitForCompletion(build);
        j.assertBuildStatus(Result.ABORTED, build);
    }
}
