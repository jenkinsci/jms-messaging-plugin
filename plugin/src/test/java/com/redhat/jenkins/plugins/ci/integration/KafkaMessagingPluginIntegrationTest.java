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

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.CIMessageBuilder;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.integration.fixtures.KafkaContainer;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.DefaultTopicProvider;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.MessageUtils;
import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.TextParameterDefinition;
import hudson.model.TextParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Shell;
import hudson.util.Secret;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class KafkaMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {

    @ClassRule
    public static DockerClassRule<KafkaContainer> docker = new DockerClassRule<>(KafkaContainer.class);
    private static KafkaContainer kafka = null;

    @Before
    public void setUp() throws IOException, InterruptedException, ClassNotFoundException {
        kafka = docker.create(); // Can be moved to @BeforeClass, BUT there are tests that stops the container on purpose - breaks subsequent tests.
        Thread.sleep(3000);

        GlobalCIConfiguration gcc = GlobalCIConfiguration.get();
        gcc.setConfigs(Collections.singletonList(new KafkaMessagingProvider(
                DEFAULT_PROVIDER_NAME,
                DEFAULT_TOPIC_NAME,
                kafka.getBootstrapServersProperty(),
                kafka.getBootstrapServersProperty()
        )));

	logger.record(Class.forName("com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingWorker"), Level.INFO).capture(10000);
    }

    @After
    public void after() {
        kafka.close();
    }

    @Override
    public ProviderData getSubscriberProviderData(String topic, String variableName, String selector, MsgCheck... msgChecks) {
        return new KafkaSubscriberProviderData(
                DEFAULT_PROVIDER_NAME,
                overrideTopic(topic),
                Arrays.asList(msgChecks),
                Util.fixNull(variableName, "CI_MESSAGE"),
                60
        );
    }

    @Override
    public ProviderData getPublisherProviderData(String topic, MessageUtils.MESSAGE_TYPE type, String properties, String content) {
        return new KafkaPublisherProviderData(
                DEFAULT_PROVIDER_NAME, overrideTopic(topic), content, true
        );
    }

    @Test
    public void testVerifyModelUIPersistence() throws Exception {
        _testVerifyModelUIPersistence();
    }

    @Test
    public void testSimpleCIEventTriggerWithTextArea() throws Exception {
        _testSimpleCIEventTriggerWithTextArea("scott=123\ntom=456",
                "scott=123\ntom=456");
    }

    @Test
    public void testSimpleCIEventTriggerWithChoiceParam() throws Exception {
        _testSimpleCIEventTriggerWithChoiceParam("scott=123", "{}",
                "mychoice is scott");
    }

    @Test
    public void testSimpleCIEventSubscribe() throws Exception {
        _testSimpleCIEventSubscribe();
    }

    @Test
    public void testSimpleCIEventTriggerWithDefaultValue() throws Exception {
        _testSimpleCIEventTriggerWithDefaultValue();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheck() throws Exception {
        _testSimpleCIEventSubscribeWithCheck();
    }

    @Test
    public void testSimpleCIEventSubscribeWithTopicOverride() throws Exception {
        _testSimpleCIEventSubscribeWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverride() throws Exception {
        _testSimpleCIEventSubscribeWithCheckWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic();
    }

    @Test
    public void testSimpleCIEventTriggerWithPipelineSendMsg() throws Exception {
        _testSimpleCIEventTriggerWithPipelineSendMsg();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineSendMsg() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithPipelineSendMsg();
    }

    @Test
    public void testSimpleCIEventTrigger() throws Exception {
        _testSimpleCIEventTrigger();
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
    public void testSimpleCIEventTriggerWithTopicOverride() throws Exception {
        _testSimpleCIEventTriggerWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventTriggerWithMultipleTopics() throws Exception {
        _testSimpleCIEventTriggerWithMultipleTopics();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverride() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic();
    }

      // No headers for Kafka.  Check for CI_MESSAGE_RECORD instead.
    @Test
    public void testSimpleCIEventTriggerRecordInEnv() throws Exception {
        FreeStyleProject jobB = j.createFreeStyleProject();
        String expected = "\"topic\":\"topic\",\"partition\":0";
        _testSimpleCIEventTriggerHeadersInEnv(jobB, "CI_MESSAGE_RECORD", expected);
    }

    @Test
    public void testSimpleCIEventSubscribeWithNoParamOverride() throws Exception {
        _testSimpleCIEventSubscribeWithNoParamOverride();
    }

    @Test
    public void testSimpleCIEventTriggerOnPipelineJob() throws Exception {
        _testSimpleCIEventTriggerOnPipelineJob();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJob() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJob();
    }

    @Test
    public void testSimpleCIEventTriggerWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithPipelineWaitForMsg();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg();
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipeline() throws Exception {
        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "foo");
        String expected = "scott = abcdefg";
        _testSimpleCIEventSendAndWaitPipeline(send, expected);
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "foo");
        String expected = "scott = abcdefg";
        String selector = "JMSDestination = 'topic://";
        _testSimpleCIEventSendAndWaitPipelineWithVariableTopic(send, selector, expected);
    }

    @Test
    public void testJobRename() throws Exception {
        _testJobRename();
    }

    @Test
    public void testJobRenameWithCheck() throws Exception {
        _testJobRenameWithCheck();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggered() throws Exception {
        _testDisabledJobDoesNotGetTriggered();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggeredWithCheck() throws Exception {
        _testDisabledJobDoesNotGetTriggeredWithCheck();
    }

    @Test
    public void testDisabledWorkflowJobDoesNotGetTriggered() throws Exception {
        _testDisabledWorkflowJobDoesNotGetTriggered();
    }

    @Test
    public void testEnsureFailedSendingOfMessageFailsBuild() throws Exception {
        stopContainer(kafka);
        System.out.println("Waiting 30 secs");
        Thread.sleep(30000);
        _testEnsureFailedSendingOfMessageFailsBuild();
    }

    @Test
    public void testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
        stopContainer(kafka);
        System.out.println("Waiting 30 secs");
        Thread.sleep(30000);
        _testEnsureFailedSendingOfMessageFailsPipelineBuild();
    }

    @Test
    public void testEnvVariablesWithPipelineWaitForMsg() throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('built-in') {\n" +
            "  def messageContent = waitForCIMessage  providerName: '" + DEFAULT_PROVIDER_NAME + "', variable: \"CI_MESSAGE_TEST\"\n" +
            "  echo \"messageContent = \" + messageContent  \n" +
            "  echo \"CI_MESSAGE_TEST = \" + CI_MESSAGE_TEST  \n" +
            "  if (env.CI_MESSAGE_TEST == null) {\n" +
            "    error(\"CI_MESSAGE_TEST not set\")\n"+
            "  }\n" +
            "  echo \"CI_MESSAGE_TEST_RECORD = \" + env.CI_MESSAGE_TEST_RECORD  \n" +
            "  if (env.CI_MESSAGE_TEST_RECORD == null) {\n" +
            "    error(\"CI_MESSAGE_TEST_RECORD not set\")\n"+
            "  }\n" +
            "  if (!env.CI_MESSAGE_TEST_RECORD.contains(\"topic\")) {\n" +
            "    error(\"topic not found\")\n"+
            "  }\n" +
            "}", true));

        scheduleAwaitStep(wait);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(null, null, null, "Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilScheduledBuildCompletes();
        j.assertBuildStatusSuccess(wait.getLastBuild());
        j.assertLogContains("Hello World", wait.getLastBuild());
    }

    @Test
    public void testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        _testAbortWaitingForMessageWithPipelineBuild();
    }

    @Test
    public void testSimpleCIEventTriggerOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {
        _testSimpleCIEventTriggerOnPipelineJobWithGlobalEnvVarInTopic();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic();
    }

    @Test
    public void testPipelineJobProperties() throws Exception {
        List<Thread> leftoverFromPreviousRuns = getThreadsByName("CIBuildTrigger.*");
        for (Thread thread : leftoverFromPreviousRuns) {
            thread.interrupt();

	    for (int i = 0; getCurrentThreadCountForName(thread.getName()) != 0 && i < 10; i++) {
                Thread.sleep(1000);
	    }
        }

        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "send");
        send.setDefinition(new CpsFlowDefinition(
                "node('built-in') {\n sendCIMessage" +
                " providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " failOnError: true, " +
                " messageContent: '" + MESSAGE_CHECK_CONTENT + "', " +
                " messageProperties: 'CI_STATUS2 = ${CI_STATUS2}', " +
                " messageType: 'CodeQualityChecksDone'}", true));

        //[expectedValue: number + '0.0234', field: 'CI_STATUS2']
        String pd = "providerList: [[$class: 'KafkaSubscriberProviderData', name: '" + DEFAULT_PROVIDER_NAME + "']]";
        WorkflowJob receive = j.jenkins.createProject(WorkflowJob.class, "receive");
        receive.addProperty(new ParametersDefinitionProperty(
                new TextParameterDefinition("CI_MESSAGE", "", "")
        ));
        receive.setDefinition(new CpsFlowDefinition(
                "def number = currentBuild.getNumber().toString()\n" +
                "properties(\n" +
                "    [\n" +
                "        pipelineTriggers(\n" +
                "            [[$class: 'CIBuildTrigger', noSquash: false, " + pd + "]]\n" +
                "        )\n" +
                "    ]\n" +
                ")\nnode('built-in') {\n sleep 1\n}", true));

        j.buildAndAssertSuccess(receive);
        // Allow some time for trigger thread stop/start.
        Thread.sleep(2000);
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        j.configRoundtrip(receive);

        j.buildAndAssertSuccess(receive);
        Thread.sleep(2000);
        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        //checks: [[expectedValue: '0.0234', field: 'CI_STATUS2']]
        String randomNumber = "123456789";
        for (int i = 0; i < 3; i++) {
            QueueTaskFuture<WorkflowRun> build = send.scheduleBuild2(0, new ParametersAction(new TextParameterValue("CI_STATUS2", randomNumber, "")));
            j.assertBuildStatusSuccess(build);
        }

        Thread.sleep(5000);
        assertEquals("there are not 5 builds", 5, receive.getLastBuild().getNumber());

        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        pd = "providerList: [[$class: 'KafkaSubscriberProviderData', checks: [[field: '" + MESSAGE_CHECK_FIELD
                + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']], name: 'test']]";
        scheduleAwaitStep(receive);

        for (int i = 0; i < 3; i++) {
            QueueTaskFuture<WorkflowRun> build = send.scheduleBuild2(0, new ParametersAction(new TextParameterValue("CI_STATUS2", randomNumber, "")));
            j.assertBuildStatusSuccess(build);
            Thread.sleep(1000);
        }

        Thread.sleep(2000);
        assertEquals("there are not 9 builds", 9, receive.getLastBuild().getNumber());

        for (int i = 1; i < 8; i++) {
            j.assertBuildStatusSuccess(receive.getBuildByNumber(i));
        }
        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));
    }

    @Test
    public void testPipelineInvalidProvider() throws Exception {
        _testPipelineInvalidProvider();
    }
}
