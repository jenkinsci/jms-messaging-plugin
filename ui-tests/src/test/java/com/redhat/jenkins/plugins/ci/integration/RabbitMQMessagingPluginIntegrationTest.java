package com.redhat.jenkins.plugins.ci.integration;

import com.google.inject.Inject;
import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.FedmsgRelayContainer;
import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.RabbitMQRelayContainer;
import com.redhat.jenkins.plugins.ci.integration.po.*;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;
import static org.junit.Assert.assertTrue;

/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
@WithPlugins({"jms-messaging", "dumpling"})
@WithDocker
public class RabbitMQMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {
    @Inject private DockerContainerHolder<RabbitMQRelayContainer> docker;

    private RabbitMQRelayContainer rabbitmq = null;
    private static final int INIT_WAIT = 360;

    @Test
    public void testGlobalConfigTestConnection() throws Exception {
    }

    @Test
    public void testAddDuplicateMessageProvider() throws IOException {
        jenkins.configure();
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        RabbitMQMessagingProvider msgConfig = new RabbitMQMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .hostname(rabbitmq.getHost())
                .portNumber(rabbitmq.getPort())
                .virtualHost("/")
                .topic("CI")
                .exchange("amq.fanout")
                .queue("")
                .userNameAuthentication("guest", "guest");
        _testAddDuplicateMessageProvider();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheck() throws Exception {
        _testSimpleCIEventSubscribeWithCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithTextArea() {
        _testSimpleCIEventTriggerWithTextArea("{ \"message\": \"Hello\\nWorld\" }",
                "Hello\\nWorld");
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverride() throws Exception, InterruptedException {
        _testSimpleCIEventSubscribeWithCheckWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineSendMsg() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithPipelineSendMsg();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheck() throws Exception {
        _testSimpleCIEventTriggerWithCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckNoSquash() throws Exception {
        _testSimpleCIEventTriggerWithCheckNoSquash();
    }

    @Test
    public void testSimpleCIEventTriggerWithRegExpCheck() throws Exception {
        _testSimpleCIEventTriggerWithRegExpCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverride() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventTriggerWithMultipleTopics() throws Exception {
        _testSimpleCIEventTriggerWithMultipleTopics();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverrideAndRestart() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithTopicOverrideAndRestart();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJob() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJob();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventSendAndWaitPipeline() throws Exception {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage providerName: 'test'," +
                " topic: 'org.fedoraproject.otopic'" +
                "\necho \"scott = \" + scott}");
        wait.save();
        wait.startBuild();

        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        send.configure();
        send.script.set("node('master') {\n sendCIMessage" +
                " providerName: 'test', " +
                " topic: 'org.fedoraproject.otopic'," +
                " messageContent: '{\"content\":\"abcdefg\"}'}");
        send.save();
        send.startBuild().shouldSucceed();

        String expected = "scott = {\"content\":\"abcdefg\"}";
        elasticSleep(1000);
        wait.getLastBuild().shouldSucceed();
        assertThat(wait.getLastBuild().getConsole(), containsString(expected));
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n" +
                "    env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                "    def scott = waitForCIMessage providerName: \"test\", overrides: [topic: \"${env.MY_TOPIC}\"]\n" +
                "    echo \"scott = \" + scott\n" +
                "}");
        wait.save();
        wait.startBuild();

        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        send.configure();
        send.script.set("node('master') {\n" +
                " env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                " sendCIMessage providerName: \"test\", overrides: [topic: \"${env.MY_TOPIC}\"], messageContent: '{ \"content\" : \"abcdef\" }'\n" +
                "}");
        send.save();
        send.startBuild().shouldSucceed();

        String expected = "scott = {\"content\":\"abcdef\"}";
        elasticSleep(1000);
        wait.getLastBuild().shouldSucceed();
        assertThat(wait.getLastBuild().getConsole(), containsString(expected));
    }

    @Test
    public void testJobRenameWithCheck() throws Exception {
        _testJobRenameWithCheck();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggeredWithCheck() throws Exception {
        _testDisabledJobDoesNotGetTriggeredWithCheck();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic();
    }

    // RabbitMQ doesn't use MessageType or MessageProperties, so this test doesn't work
    //@Test
    //public void testEnsureFailedSendingOfMessageFailsBuild() throws Exception {
    //}

    // RabbitMQ doesn't use MessageType or MessageProperties, so this test doesn't work
    //@WithPlugins("workflow-aggregator")
    //@Test
    //public void testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
    //}

    @WithPlugins("workflow-aggregator")
    @Test
    public void testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        _testAbortWaitingForMessageWithPipelineBuild();
    }

    @Before
    public void setUp() throws Exception {
        rabbitmq = docker.get();
        jenkins.configure();
        elasticSleep(5000);
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        RabbitMQMessagingProvider msgConfig = new RabbitMQMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .hostname(rabbitmq.getHost())
                .portNumber(rabbitmq.getPort())
                .virtualHost("/")
                .topic("CI")
                .exchange("amq.fanout")
                .queue("")
                .userNameAuthentication("guest", "guest");

        int counter = 0;
        boolean connected = false;
        while (counter < INIT_WAIT) {
            try {
                msgConfig.testConnection();
                waitFor(driver, hasContent("Successfully connected to " + rabbitmq.getHost() + ":" + rabbitmq.getPort()), 5);
                connected = true;
                break;
            } catch (Exception e) {
                counter++;
                elasticSleep(1000);
            }
        }
        if (!connected) {
            throw new Exception("Did not get connection successful message in " + INIT_WAIT + " secs.");
        }
        elasticSleep(1000);
        jenkins.save();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testPipelineInvalidProvider() throws Exception {
        _testPipelineInvalidProvider();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testPipelineSendMsgReturnMessage() throws Exception {
        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);
        job.script.set("node('master') {\n def message = sendCIMessage " +
                " providerName: 'test', " +
                " messageContent: '', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'\n"  +
                " echo message.getMessageId()\necho message.getMessageContent()\n}");
        job.sandbox.check(true);
        job.save();
        job.startBuild().shouldSucceed();
        // See https://github.com/jenkinsci/jms-messaging-plugin/issues/125
        // timestamp == 0 indicates timestamp was not set in message
        assertThat(job.getLastBuild().getConsole(), Matchers.not(containsString("\"timestamp\":0")));
    }

    @Test
    public void testSimpleCIEventSubscribeWithQueueOverride() throws Exception, InterruptedException {
        FreeStyleJob job = jenkins.jobs.create();
        job.configure();

        CISubscriberBuildStep subscriber = job.addBuildStep(CISubscriberBuildStep.class);
        subscriber.overrides.check();
        subscriber.queue.set("queue");

        job.save();
        elasticSleep(1000);
        job.scheduleBuild();
        job.getLastBuild().shouldFail().shouldExist();
        assertThat(job.getLastBuild().getConsole(), containsString("ERROR: Connection to broker can't be established!"));
    }

    @Test
    public void testFedoraMessagingHeaders() throws Exception {
        FreeStyleJob job = jenkins.jobs.create();
        job.configure();
        CINotifierPostBuildStep notifier = job.addPublisher(CINotifierPostBuildStep.class);
        notifier.fedoraMessaging.click();
        notifier.severity.select("20");
        notifier.schema.sendKeys("schema");
        job.save();
        job.startBuild().shouldSucceed();

        elasticSleep(1000);
        job.getLastBuild().shouldSucceed().shouldExist();
        assertThat(job.getLastBuild().getConsole(), containsString(
                "fedora_messaging_severity=20, fedora_messaging_schema=schema}"));
        assertThat(job.getLastBuild().getConsole(), containsString(
                "{sent_at="));
    }
}
