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
package com.redhat.jenkins.plugins.ci.integration;

import com.google.common.base.Objects;
import com.google.common.base.MoreObjects;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.rabbitmq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.integration.fixtures.RabbitMQRelayContainer;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQSubscriberProviderData;
import com.redhat.utils.MessageUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.Secret;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RabbitMQMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {
    @ClassRule
    public static DockerClassRule<RabbitMQRelayContainer> docker = new DockerClassRule<>(RabbitMQRelayContainer.class);
    private static RabbitMQRelayContainer rabbitmq = null;

    @BeforeClass
    public static void startBroker() throws Exception {
        rabbitmq = docker.create();
    }

    @Before
    public void setUp() {
        GlobalCIConfiguration.get().setConfigs(Collections.singletonList(new RabbitMQMessagingProvider(
                DEFAULT_PROVIDER_NAME, "/", rabbitmq.getIpAddress(), rabbitmq.getPort(), "CI", "amq.fanout", "",
                new UsernameAuthenticationMethod("guest", Secret.fromString("guest"))
        )));

        // TODO Test connection
    }

    @Override
    public ProviderData getSubscriberProviderData(String topic, String variableName, String selector, MsgCheck... msgChecks) {
        return new RabbitMQSubscriberProviderData(
                DEFAULT_PROVIDER_NAME,
                overrideTopic(topic),
                Arrays.asList(msgChecks),
                MoreObjects.firstNonNull(variableName, "CI_MESSAGE"),
                60
        );
    }

    @Override
    public ProviderData getPublisherProviderData(String topic, MessageUtils.MESSAGE_TYPE type, String properties, String content) {
        return new RabbitMQPublisherProviderData(
                DEFAULT_PROVIDER_NAME, overrideTopic(topic), content, true, true, 20, "schema"
        );
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheck() throws Exception {
        _testSimpleCIEventSubscribeWithCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithTextArea() throws Exception {
        _testSimpleCIEventTriggerWithTextArea("{ \"message\": \"Hello\\nWorld\" }",
                "Hello\\nWorld");
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverride() throws Exception {
        _testSimpleCIEventSubscribeWithCheckWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic();
    }

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
    public void testSimpleCIEventTriggerWithCheckOnPipelineJob() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJob();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg();
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipeline() throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n def scott = waitForCIMessage providerName: '" + DEFAULT_PROVIDER_NAME + "'," +
                " overrides: [topic: 'org.fedoraproject.otopic']" +
                "\necho \"scott = \" + scott}", true));
        scheduleAwaitStep(wait);

        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "send");
        send.setDefinition(new CpsFlowDefinition("node('master') {\n sendCIMessage" +
                " providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " overrides: [topic: 'org.fedoraproject.otopic']," +
                " messageContent: '{\"content\":\"abcdefg\"}'}", true));
        j.buildAndAssertSuccess(send);

        waitUntilScheduledBuildCompletes();
        WorkflowRun lastBuild = wait.getLastBuild();
        j.assertBuildStatusSuccess(lastBuild);
        j.assertLogContains("scott = {\"content\":\"abcdefg\"}", lastBuild);
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n" +
                "    env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                "    def scott = waitForCIMessage providerName: '" + DEFAULT_PROVIDER_NAME + "', overrides: [topic: \"${env.MY_TOPIC}\"]\n" +
                "    echo \"scott = \" + scott\n" +
                "}", true));
        scheduleAwaitStep(wait);

        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "send");
        send.setDefinition(new CpsFlowDefinition("node('master') {\n" +
                " env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                " sendCIMessage providerName: '" + DEFAULT_PROVIDER_NAME + "', overrides: [topic: \"${env.MY_TOPIC}\"], messageContent: '{ \"content\" : \"abcdef\" }'\n" +
                "}", true));
        send.save();
        j.buildAndAssertSuccess(send);

        waitUntilScheduledBuildCompletes();
        WorkflowRun lastBuild = wait.getLastBuild();
        j.assertBuildStatusSuccess(lastBuild);
        j.assertLogContains("scott = {\"content\":\"abcdef\"}", lastBuild);
    }

    @Test
    public void testJobRenameWithCheck() throws Exception {
        _testJobRenameWithCheck();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggeredWithCheck() throws Exception {
        _testDisabledJobDoesNotGetTriggeredWithCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic();
    }

    @Test
    public void testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        _testAbortWaitingForMessageWithPipelineBuild();
    }


    @Test
    public void testPipelineInvalidProvider() throws Exception {
        _testPipelineInvalidProvider();
    }

    @Test
    public void testPipelineSendMsgReturnMessage() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "job");
        job.setDefinition(new CpsFlowDefinition("node('master') {\n def message = sendCIMessage " +
                " providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " messageContent: '', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'\n" +
                " echo message.getMessageId()\necho message.getMessageContent()\n}", true));
        j.buildAndAssertSuccess(job);
        // See https://github.com/jenkinsci/jms-messaging-plugin/issues/125
        // timestamp == 0 indicates timestamp was not set in message
        j.assertLogNotContains("\"timestamp\":0", job.getLastBuild());
    }

    @Test
    public void testFedoraMessagingHeaders() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        job.getPublishersList().add(new CIMessageNotifier(new RabbitMQPublisherProviderData(
                "test", null, "", true, true, 20, "schema"
        )));
        FreeStyleBuild lastBuild = j.buildAndAssertSuccess(job);

        j.assertLogContains("fedora_messaging_severity=20, fedora_messaging_schema=schema}", lastBuild);
        j.assertLogContains("{sent_at=", lastBuild);
    }
}
