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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.rabbitmq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.integration.fixtures.RabbitMQRelayContainer;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQSubscriberProviderData;

import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

public class RabbitMQMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {
    @ClassRule
    public static DockerClassRule<RabbitMQRelayContainer> docker = new DockerClassRule<>(RabbitMQRelayContainer.class);
    private static RabbitMQRelayContainer rabbitmq = null;

    @Before
    public void setUp() throws Exception, IOException, InterruptedException {
        rabbitmq = docker.create();
        if (!waitForProviderToBeReady(rabbitmq.getCid(), "Starting broker... completed with 0 plugins.")) {
            throw new Exception("RabbitMQ provider container is not ready");
        }

        addUsernamePasswordCredential("rabbitmq-username-password", "guest", "guest");

        GlobalCIConfiguration.get()
                .setConfigs(Collections.singletonList(new RabbitMQMessagingProvider(DEFAULT_PROVIDER_NAME, "/",
                        rabbitmq.getIpAddress(), rabbitmq.getPort(), "CI", "amq.fanout", "",
                        new UsernameAuthenticationMethod("rabbitmq-username-password"))));

        logger.record("com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingWorker", Level.INFO);
        logger.quiet();
        logger.capture(5000);
    }

    @After
    public void after() {
        rabbitmq.close();
    }

    @Override
    public ProviderData getSubscriberProviderData(String provider, String topic, String variableName, Boolean useFiles,
            String selector, MsgCheck... msgChecks) {
        return new RabbitMQSubscriberProviderData(provider, overrideTopic(topic), Arrays.asList(msgChecks),
                Util.fixNull(variableName, "CI_MESSAGE"), useFiles, 60);
    }

    @Override
    public ProviderData getPublisherProviderData(String provider, String topic, String properties, String content) {
        return new RabbitMQPublisherProviderData(provider, overrideTopic(topic), content, true, true, 20, "schema");
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheck() throws Exception {
        _testSimpleCIEventSubscribeWithCheck();
    }

    @Test
    public void testSimpleCIEventTriggerWithTextArea() throws Exception {
        _testSimpleCIEventTriggerWithTextArea("{ \"message\": \"Hello\\nWorld\" }", "Hello\\nWorld");
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
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData(testName.getMethodName(), null, null);
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd), true));
        scheduleAwaitStep(jobA);

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");
        pd = getPublisherProviderData(testName.getMethodName(), null, "{\"content\":\"abcdefg\"}");
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));
        j.buildAndAssertSuccess(jobB);

        waitUntilScheduledBuildCompletes();
        WorkflowRun lastBuild = jobA.getLastBuild();
        j.assertBuildStatusSuccess(lastBuild);
        j.assertLogContains("message = {\"content\":\"abcdefg\"}", lastBuild);

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData("${env.MY_TOPIC}", null, null);
        jobA.setDefinition(new CpsFlowDefinition(
                buildWaitForCIMessageScript(pd, "env.MY_TOPIC = \"" + testName.getMethodName() + "\""), true));

        scheduleAwaitStep(jobA);

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");
        pd = getPublisherProviderData("${env.MY_TOPIC}", null, "{\"content\":\"abcdefg\"}");
        jobB.setDefinition(new CpsFlowDefinition(
                buildSendCIMessageScript(pd, "env.MY_TOPIC = \"" + testName.getMethodName() + "\""), true));

        jobB.save();
        j.buildAndAssertSuccess(jobB);

        waitUntilScheduledBuildCompletes();
        WorkflowRun lastBuild = jobA.getLastBuild();
        j.assertBuildStatusSuccess(lastBuild);
        j.assertLogContains("message = {\"content\":\"abcdefg\"}", lastBuild);

        jobA.delete();
        jobB.delete();
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
        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "job");
        ProviderData pd = getPublisherProviderData(testName.getMethodName(), null, MESSAGE_CHECK_CONTENT);
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));
        j.buildAndAssertSuccess(jobB);
        // See https://github.com/jenkinsci/jms-messaging-plugin/issues/125
        // timestamp == 0 indicates timestamp was not set in message
        j.assertLogNotContains("\"timestamp\":0", jobB.getLastBuild());

        jobB.delete();
    }

    @Test
    public void testFedoraMessagingHeaders() throws Exception {
        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(new RabbitMQPublisherProviderData("test", null, "", true, true, 20, "schema")));
        FreeStyleBuild lastBuild = j.buildAndAssertSuccess(jobB);

        j.assertLogContains("fedora_messaging_severity=20, fedora_messaging_schema=schema}", lastBuild);
        j.assertLogContains("{sent_at=", lastBuild);

        jobB.delete();
    }

    @Test
    public void testCITriggerWithFileParameter() throws Exception {
        _testCITriggerWithFileParameter(List.of("CI_MESSAGE"));
    }

    @Test
    public void testWaitForCIMessageStepWithFiles() throws Exception {
        _testWaitForCIMessageStepWithFiles(List.of("CI_MESSAGE"));
    }

    @Test
    public void testWaitForCIMessagePipelineWithFiles() throws Exception {
        _testWaitForCIMessagePipelineWithFiles(List.of("CI_MESSAGE"));
    }
}
