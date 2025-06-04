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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.integration.fixtures.KafkaContainer;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.MessageUtils;

import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.TextParameterDefinition;
import hudson.model.TextParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Shell;

public class KafkaMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {

    @ClassRule
    public static DockerClassRule<KafkaContainer> docker = new DockerClassRule<>(KafkaContainer.class);
    private static KafkaContainer kafka = null;

    @Before
    public void setUp() throws Exception, IOException, InterruptedException {
        kafka = docker.create();
        if (!waitForProviderToBeReady(kafka.getCid(), "Kafka Server started (kafka.server.KafkaRaftServer)")) {
            throw new Exception("Kafka provider container is not ready");
        }

        GlobalCIConfiguration gcc = GlobalCIConfiguration.get();
        gcc.setConfigs(Collections.singletonList(new KafkaMessagingProvider(DEFAULT_PROVIDER_NAME, DEFAULT_TOPIC_NAME,
                kafka.getBootstrapServersProperty(), kafka.getBootstrapServersProperty())));

        logger.record("com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingWorker", Level.INFO);
        logger.record("org.apache.kafka.clients.consumer.internals.SubscriptionState", Level.INFO);
        logger.quiet();
        logger.capture(5000);
    }

    @After
    public void after() throws IOException, InterruptedException {
        kafka.close();
    }

    @Override
    public ProviderData getSubscriberProviderData(String topic, String variableName, String selector,
            MsgCheck... msgChecks) {
        return new KafkaSubscriberProviderData(DEFAULT_PROVIDER_NAME, overrideTopic(topic),
                "group.id=" + testName.getMethodName(), Arrays.asList(msgChecks),
                Util.fixNull(variableName, "CI_MESSAGE"), 60);
    }

    @Override
    public ProviderData getPublisherProviderData(String topic, MessageUtils.MESSAGE_TYPE type, String properties,
            String content) {
        return new KafkaPublisherProviderData(DEFAULT_PROVIDER_NAME, overrideTopic(topic), "", content, true);
    }

    @Override
    protected boolean additionalWaitForReceiverToBeReadyCheck(String jobname, int occurrences) {
        int count = 0;
        for (String s : logger.getMessages()) {
            if (s.contains("Resetting offset for partition")) {
                count++;
            }
        }
        if (count >= occurrences) {
            System.out.println("----------------------- REALLY GOT IT");
            return true;
        } else {
            System.out.println("----------------------- NO, STILL LOOKING");
        }
        return false;
    }

    @Test
    public void testVerifyModelUIPersistence() throws Exception {
        _testVerifyModelUIPersistence();
    }

    @Test
    public void testSimpleCIEventTriggerWithTextArea() throws Exception {
        _testSimpleCIEventTriggerWithTextArea("scott=123\ntom=456", "scott=123\ntom=456");
    }

    @Test
    public void testSimpleCIEventTriggerWithChoiceParam() throws Exception {
        _testSimpleCIEventTriggerWithChoiceParam("scott=123", "{}", "mychoice is scott");
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

    // No headers for Kafka. Check for CI_MESSAGE_RECORD instead.
    @Test
    public void testSimpleCIEventTriggerRecordInEnv() throws Exception {
        FreeStyleProject jobB = j.createFreeStyleProject();
        String expected = "\"topic\":\"" + testName.getMethodName() + "\",\"partition\":0";
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
        // For Kafka, when the job is enabled *both* messages will be received.
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo BUILD_NUMBER = $BUILD_NUMBER"));
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"))),
                jobA);
        waitForReceiverToBeReady(jobA.getFullName(), 1);
        jobA.disable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(),
                MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", null)));
        j.buildAndAssertSuccess(jobB);

        // Wait to make sure job doesn't run.
        Thread.sleep(3000);
        assertThat(jobA.getBuilds(), Matchers.iterableWithSize(0));

        jobA.enable();
        waitForReceiverToBeReady(jobA.getFullName(), 2, true);

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        assertEquals("Latest build number", 2, jobA.getLastBuild().getNumber());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggeredWithCheck() throws Exception {
        // For Kafka, when the job is enabled *both* messages will be received.
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo BUILD_NUMBER = $BUILD_NUMBER"));
        waitForReceiverToBeReady(jobA.getFullName(), 1);
        jobA.disable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), null, null, MESSAGE_CHECK_CONTENT)));
        j.buildAndAssertSuccess(jobB);

        // Wait to make sure job doesn't run.
        Thread.sleep(3000);

        assertThat(jobA.getBuilds(), Matchers.iterableWithSize(0));

        jobA.enable();
        waitForReceiverToBeReady(jobA.getFullName(), 2, true);

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        assertEquals("Latest build number", 2, jobA.getLastBuild().getNumber());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testDisabledWorkflowJobDoesNotGetTriggered() throws Exception {
        // For Kafka, when the job is enabled *both* messages will be received.
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("echo \"BUILD_NUMBER = ${env.BUILD_NUMBER}\"", true));
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"))),
                jobA);
        jobA.doDisable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(),
                MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", null)));

        j.buildAndAssertSuccess(jobB);

        // Wait to make sure job doesn't run.
        Thread.sleep(3000);

        assertThat(jobA.getBuilds(), Matchers.iterableWithSize(0));

        jobA.doEnable();
        waitForReceiverToBeReady(jobA.getFullName(), 2, true);

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        assertEquals("Latest build number", 2, jobA.getLastBuild().getNumber());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testEnsureFailedSendingOfMessageFailsBuild() throws Exception {
        waitForProviderToStop(kafka.getCid());
        _testEnsureFailedSendingOfMessageFailsBuild();
    }

    @Test
    public void testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
        waitForProviderToStop(kafka.getCid());
        _testEnsureFailedSendingOfMessageFailsPipelineBuild();
    }

    @Test
    public void testEnvVariablesWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n"
                + "  def messageContent = waitForCIMessage  providerName: '" + DEFAULT_PROVIDER_NAME
                + "', variable: \"CI_MESSAGE_TEST\"," + "  overrides: [topic: '" + testName.getMethodName() + "']\n"
                + "  echo \"messageContent = \" + messageContent  \n"
                + "  echo \"CI_MESSAGE_TEST = \" + CI_MESSAGE_TEST  \n" + "  if (env.CI_MESSAGE_TEST == null) {\n"
                + "    error(\"CI_MESSAGE_TEST not set\")\n" + "  }\n"
                + "  echo \"CI_MESSAGE_TEST_RECORD = \" + env.CI_MESSAGE_TEST_RECORD  \n"
                + "  if (env.CI_MESSAGE_TEST_RECORD == null) {\n" + "    error(\"CI_MESSAGE_TEST_RECORD not set\")\n"
                + "  }\n" + "  if (!env.CI_MESSAGE_TEST_RECORD.contains(\"topic\")) {\n"
                + "    error(\"topic not found\")\n" + "  }\n" + "}", true));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, null, "Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilScheduledBuildCompletes();
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
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

    // @Test
    public void testPipelineJobProperties() throws Exception {
        List<Thread> leftoverFromPreviousRuns = getThreadsByName("CIBuildTrigger.*");
        for (Thread thread : leftoverFromPreviousRuns) {
            thread.interrupt();

            for (int i = 0; getCurrentThreadCountForName(thread.getName()) != 0 && i < 10; i++) {
                Thread.sleep(1000);
            }
        }

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");
        jobB.setDefinition(new CpsFlowDefinition("node('built-in') {\n sendCIMessage" + " providerName: '"
                + DEFAULT_PROVIDER_NAME + "', " + " failOnError: true, " + " messageContent: '" + MESSAGE_CHECK_CONTENT
                + "', " + " messageProperties: 'CI_STATUS2 = ${CI_STATUS2}', "
                + " messageType: 'CodeQualityChecksDone'}", true));

        // [expectedValue: number + '0.0234', field: 'CI_STATUS2']
        String pd = "providerList: [[$class: 'KafkaSubscriberProviderData', name: '" + DEFAULT_PROVIDER_NAME + "']]";
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "receive-new");
        jobA.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition("CI_MESSAGE", "", "")));
        jobA.setDefinition(
                new CpsFlowDefinition("def number = currentBuild.getNumber().toString()\n" + "properties(\n" + "    [\n"
                        + "        pipelineTriggers(\n" + "            [[$class: 'CIBuildTrigger', noSquash: false, "
                        + pd + "]]\n" + "        )\n" + "    ]\n" + ")\nnode('built-in') {\n sleep 1\n}", true));

        j.buildAndAssertSuccess(jobA);
        // Allow some time for trigger thread stop/start.
        Thread.sleep(2000);
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        j.configRoundtrip(jobA);

        j.buildAndAssertSuccess(jobA);
        Thread.sleep(2000);
        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        // checks: [[expectedValue: '0.0234', field: 'CI_STATUS2']]
        String randomNumber = "123456789";
        for (int i = 0; i < 3; i++) {
            QueueTaskFuture<WorkflowRun> build = jobB.scheduleBuild2(0,
                    new ParametersAction(new TextParameterValue("CI_STATUS2", randomNumber, "")));
            j.assertBuildStatusSuccess(build);
        }

        Thread.sleep(5000);
        assertEquals("there are not 5 builds", 5, jobA.getLastBuild().getNumber());

        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        pd = "providerList: [[$class: 'KafkaSubscriberProviderData', checks: [[field: '" + MESSAGE_CHECK_FIELD
                + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']], name: 'test']]";
        scheduleAwaitStep(jobA);

        for (int i = 0; i < 3; i++) {
            QueueTaskFuture<WorkflowRun> build = jobB.scheduleBuild2(0,
                    new ParametersAction(new TextParameterValue("CI_STATUS2", randomNumber, "")));
            j.assertBuildStatusSuccess(build);
            Thread.sleep(1000);
        }

        Thread.sleep(2000);
        assertEquals("there are not 9 builds", 9, jobA.getLastBuild().getNumber());

        for (int i = 1; i < 8; i++) {
            j.assertBuildStatusSuccess(jobA.getBuildByNumber(i));
        }
        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testPipelineInvalidProvider() throws Exception {
        _testPipelineInvalidProvider();
    }
}
