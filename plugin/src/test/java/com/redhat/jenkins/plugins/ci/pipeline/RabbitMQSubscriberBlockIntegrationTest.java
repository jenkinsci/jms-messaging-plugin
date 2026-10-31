package com.redhat.jenkins.plugins.ci.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.rabbitmq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;

import hudson.model.Result;

/** Run against an isolated broker with -Drabbitmq.test.port=PORT. */
public class RabbitMQSubscriberBlockIntegrationTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private Connection connection;
    private String exchange;

    @Before
    public void setup() throws Exception {
        String port = System.getProperty("rabbitmq.test.port");
        assumeTrue("Set rabbitmq.test.port to run real-broker tests", port != null);
        String user = System.getProperty("rabbitmq.test.user", "jms-test");
        String password = System.getProperty("rabbitmq.test.password", "jms-test");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(Integer.parseInt(port));
        factory.setUsername(user);
        factory.setPassword(password);
        connection = factory.newConnection();
        exchange = "ready-test-" + UUID.randomUUID();
        try (Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, "topic", false);
        }
        SystemCredentialsProvider.getInstance().getCredentials().add(new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, "broker-test", "Isolated test broker", user, password));
        GlobalCIConfiguration.get()
                .setConfigs(Collections
                        .singletonList(new RabbitMQMessagingProvider("test", "/", "127.0.0.1", Integer.parseInt(port),
                                "reply", exchange, "", new UsernameAuthenticationMethod("broker-test"))));
    }

    @After
    public void cleanup() throws Exception {
        if (connection != null) {
            RabbitMQMessagingProvider provider = (RabbitMQMessagingProvider) GlobalCIConfiguration.get()
                    .getProvider("test");
            if (provider.getConnection() != null && provider.getConnection().isOpen()) {
                provider.getConnection().close();
            }
            try (Channel channel = connection.createChannel()) {
                channel.exchangeDelete(exchange);
            } finally {
                connection.close();
            }
        }
    }

    private WorkflowJob job(String body) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("node { def reply = withCIMessage(providerData: "
                + "[$class: 'RabbitMQSubscriberProviderData', name: 'test', timeout: 1]) { " + body
                + " }; echo 'REPLY=' + reply }", true));
        return job;
    }

    private void assertTemporaryQueueDeleted(WorkflowRun build) throws Exception {
        Matcher matcher = Pattern.compile("RabbitMQ subscription ready: queue=([^,\\s]+),")
                .matcher(JenkinsRule.getLog(build));
        assertTrue("The broker must confirm a subscription before the body runs", matcher.find());
        String queue = matcher.group(1);
        assertNotNull(UUID.fromString(queue));
        Channel channel = connection.createChannel();
        IOException missing = assertThrows(IOException.class, () -> channel.queueDeclarePassive(queue));
        ShutdownSignalException signal = (ShutdownSignalException) missing.getCause();
        assertEquals(404, ((AMQP.Channel.Close) signal.getReason()).getReplyCode());
    }

    @Test
    public void receivesSingleImmediatePublicationWithoutSleepsAndDeletesQueue() throws Exception {
        WorkflowJob job = job("sendCIMessage(providerData: [$class: 'RabbitMQPublisherProviderData', "
                + "name: 'test', messageContent: '{\"result\":\"ok\"}', failOnError: true]); " + "echo 'BODY_DONE'");
        WorkflowRun build = j.buildAndAssertSuccess(job);
        j.assertLogContains("BODY_DONE", build);
        j.assertLogContains("REPLY={\"result\":\"ok\"}", build);
        assertTemporaryQueueDeleted(build);
    }

    @Test
    public void bodyFailureRemovesRealTemporaryQueue() throws Exception {
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job("error 'REQUEST_FAILED'").scheduleBuild2(0));
        j.assertLogContains("REQUEST_FAILED", build);
        assertTemporaryQueueDeleted(build);
    }

    @Test
    public void abortRemovesRealTemporaryQueue() throws Exception {
        WorkflowRun build = job("echo 'BODY_WAITING'; sleep time: 10, unit: 'MINUTES'").scheduleBuild2(0)
                .waitForStart();
        j.waitForMessage("BODY_WAITING", build);
        build.doStop();
        j.waitForCompletion(build);
        j.assertBuildStatus(Result.ABORTED, build);
        assertTemporaryQueueDeleted(build);
    }

    @Test
    public void existingQueueAndBindingSurviveBodyFailure() throws Exception {
        String queue = "existing-" + UUID.randomUUID();
        RabbitMQMessagingProvider previous = (RabbitMQMessagingProvider) GlobalCIConfiguration.get()
                .getProvider("test");
        GlobalCIConfiguration.get()
                .setConfigs(Collections.singletonList(new RabbitMQMessagingProvider("test", "/", previous.getHostname(),
                        previous.getPortNumber(), "reply", exchange, queue, previous.getAuthenticationMethod())));
        try (Channel channel = connection.createChannel()) {
            channel.queueDeclare(queue, true, false, false, null);
            channel.queueBind(queue, exchange, "reply");
            try {
                WorkflowRun build = j.assertBuildStatus(Result.FAILURE,
                        job("error 'REQUEST_FAILED'").scheduleBuild2(0));
                j.assertLogContains("REQUEST_FAILED", build);
                channel.queueDeclarePassive(queue);
                channel.basicPublish(exchange, "reply", null, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertNotNull("The existing binding must still route messages", channel.basicGet(queue, true));
            } finally {
                channel.queueDelete(queue);
            }
        }
    }
}
