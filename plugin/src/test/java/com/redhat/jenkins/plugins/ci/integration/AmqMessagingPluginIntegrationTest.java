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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.CIMessageBuilder;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.activemq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.integration.fixtures.ActiveMQContainer;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.TextParameterDefinition;
import hudson.model.TextParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Shell;

public class AmqMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {

    @ClassRule
    public static DockerClassRule<ActiveMQContainer> docker = new DockerClassRule<>(ActiveMQContainer.class);
    private static ActiveMQContainer amq = null;

    @Before
    public void setUp() throws Exception, IOException, InterruptedException {
        amq = docker.create(); // Could be moved to @BeforeClass but there are tests
                               // that stop the container on purpose, breaking subsequent tests.
        if (!waitForProviderToBeReady(amq.getCid(), "INFO | ActiveMQ Jolokia REST API available at")) {
            throw new Exception("AMQ provider container is not ready");
        }

        String brokerUrl = amq.getBroker();

        addUsernamePasswordCredential("amq-username-password", "admin", "redhat");

        GlobalCIConfiguration gcc = GlobalCIConfiguration.get();
        gcc.setConfigs(Collections.singletonList(new ActiveMqMessagingProvider(DEFAULT_PROVIDER_NAME, brokerUrl, false,
                DEFAULT_TOPIC_NAME, null, new UsernameAuthenticationMethod("amq-username-password"))));

        logger.record("com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingWorker", Level.INFO);
        logger.quiet();
        logger.capture(5000);
    }

    @After
    public void after() {
        amq.close();
    }

    @Override
    public ProviderData getSubscriberProviderData(String provider, String topic, String variableName, Boolean useFiles,
            String selector, MsgCheck... msgChecks) {
        return new ActiveMQSubscriberProviderData(provider, overrideTopic(topic), Util.fixNull(selector),
                Arrays.asList(msgChecks), Util.fixNull(variableName, "CI_MESSAGE"), useFiles, 60);
    }

    @Override
    public ProviderData getPublisherProviderData(String provider, String topic, String properties, String content) {
        return getPublisherProviderData(provider, topic, properties, content, 0);
    }

    public ProviderData getPublisherProviderData(String provider, String topic, String properties, String content,
            int ttl) {
        return new ActiveMQPublisherProviderData(provider, overrideTopic(topic), properties, content, true, ttl);
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
    public void testSimpleCIEventTriggerWithBooleanParam() throws Exception {
        _testSimpleCIEventTriggerWithBoolParam("scott=123\ntom=456\ndryrun=true",
                "{ \"scott\": \"123\", \"tom\": \"456\", \"dryrun\": true }", "dryrun is true, scott is 123");
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
    public void testSimpleCIEventTriggerWithWildcardInSelector() throws Exception {
        _testSimpleCIEventTriggerWithWildcardInSelector();
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

    @Test
    public void testSimpleCIEventTriggerWithParamOverride() throws Exception {
        _testSimpleCIEventTriggerWithParamOverride();
    }

    @Test
    public void testSimpleCIEventTriggerHeadersInEnv() throws Exception {
        FreeStyleProject jobB = j.createFreeStyleProject();
        String expected = "{\"CI_STATUS\":\"passed\",\"CI_NAME\":\"" + jobB.getName() + "\"";
        _testSimpleCIEventTriggerHeadersInEnv(jobB, "CI_MESSAGE_HEADERS", expected);
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
    public void testSimpleCIEventTriggerWithSelectorWithCheckWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithSelectorWithCheckWithPipelineWaitForMsg();
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipeline() throws Exception {
        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "foo");
        _testSimpleCIEventSendAndWaitPipeline(send, "message = abcdefg");
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "foo");
        _testSimpleCIEventSendAndWaitPipelineWithVariableTopic(send, "message = abcdefg");
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
        waitForProviderToStop(amq.getCid());
        _testEnsureFailedSendingOfMessageFailsBuild();
    }

    @Test
    public void testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
        waitForProviderToStop(amq.getCid());
        _testEnsureFailedSendingOfMessageFailsPipelineBuild();
    }

    @Test
    public void testEnvVariablesWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData(testName.getMethodName(), "CI_MESSAGE_TEST", null);
        String postStatements = "echo \"CI_MESSAGE_TEST = \" + CI_MESSAGE_TEST  \n"
                + "  if (env.CI_MESSAGE_TEST == null) {\n" + "    error(\"CI_MESSAGE_TEST not set\")\n" + "  }\n"
                + "  echo \"CI_MESSAGE_TEST_HEADERS = \" + env.CI_MESSAGE_TEST_HEADERS  \n"
                + "  if (env.CI_MESSAGE_TEST_HEADERS == null) {\n" + "    error(\"CI_MESSAGE_TEST_HEADERS not set\")\n"
                + "  }\n" + "  if (!env.CI_MESSAGE_TEST_HEADERS.contains(\"TEST_PROPERTY\")) {\n"
                + "    error(\"TEST_PROPERTY not found\")\n" + "  }\n";
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd, null, postStatements), true));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "TEST_PROPERTY = TEST_VALUE", "Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
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

    @Test
    public void testPipelineJobPropertiesMultipleProviders() throws Exception {
        List<Thread> leftoverFromPreviousRuns = getThreadsByName("ActiveMQ.*Task-.*");
        leftoverFromPreviousRuns.addAll(getThreadsByName("CIBuildTrigger.*"));
        for (Thread thread : leftoverFromPreviousRuns) {
            thread.interrupt();

            for (int i = 0; getCurrentThreadCountForName(thread.getName()) != 0 && i < 50; i++) {
                Thread.sleep(200);
            }
        }

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");
        jobB.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition("CI_STATUS2", "", "")));
        ProviderData pd = getPublisherProviderData(testName.getMethodName(), "CI_STATUS2 = ${CI_STATUS2}",
                MESSAGE_CHECK_CONTENT);
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));

        // [expectedValue: number + '0.0234', field: 'CI_STATUS2']
        pd = getSubscriberProviderData(testName.getMethodName(), null, "CI_NAME = '" + jobB.getName() + "'");
        CIBuildTrigger t = new CIBuildTrigger(false, Collections.singletonList(pd));
        JobPropertyStep s = new JobPropertyStep(
                Collections.singletonList(new PipelineTriggersJobProperty(Collections.singletonList(t))));
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "receive");
        jobA.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition("CI_MESSAGE", "", "")));
        jobA.setDefinition(new CpsFlowDefinition("def number = currentBuild.getNumber().toString()\n"
                + Snippetizer.object2Groovy(s) + "\nnode('built-in') {\n sleep 1\n}", true));

        j.buildAndAssertSuccess(jobA);
        waitForReceiverToBeReady(jobA.getDisplayName());
        assertEquals("ActiveMQ.*Task- count", 1, getCurrentThreadCountForName("ActiveMQ.*Task-.*"));
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        j.configRoundtrip(jobA);

        j.buildAndAssertSuccess(jobA);
        waitForReceiverToBeReady(jobA.getDisplayName(), 3);
        printThreadsWithName("ActiveMQ.*Task-.*");
        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("ActiveMQ.*Task- count", 1, getCurrentThreadCountForName("ActiveMQ.*Task-.*"));
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        // checks: [[expectedValue: '0.0234', field: 'CI_STATUS2']]
        String randomNumber = "123456789";
        for (int i = 0; i < 3; i++) {
            QueueTaskFuture<WorkflowRun> build = jobB.scheduleBuild2(0,
                    new ParametersAction(new TextParameterValue("CI_STATUS2", randomNumber, "")));
            j.assertBuildStatusSuccess(build);
        }

        waitUntilTriggeredBuildCompletes(jobA, 5);
        assertEquals("there are not 5 builds", 5, jobA.getLastBuild().getNumber());

        printThreadsWithName("ActiveMQ.*Task-.*");
        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("ActiveMQ.*Task- count", 1, getCurrentThreadCountForName("ActiveMQ.*Task-.*"));
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        pd = getSubscriberProviderData(testName.getMethodName(), null, "CI_NAME = '" + jobB.getName() + "'",
                new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE));
        t = new CIBuildTrigger(false, Collections.singletonList(pd));
        s = new JobPropertyStep(
                Collections.singletonList(new PipelineTriggersJobProperty(Collections.singletonList(t))));
        jobA.setDefinition(new CpsFlowDefinition("def number = currentBuild.getNumber().toString()\n"
                + Snippetizer.object2Groovy(s) + "\nnode('built-in') {\n sleep 1\n}", false));
        scheduleAwaitStep(jobA, 10);

        for (int i = 0; i < 3; i++) {
            QueueTaskFuture<WorkflowRun> build = jobB.scheduleBuild2(0,
                    new ParametersAction(new TextParameterValue("CI_STATUS2", randomNumber, "")));
            j.assertBuildStatusSuccess(build);
        }

        waitUntilTriggeredBuildCompletes(jobA, 9);
        assertEquals("there are not 9 builds", 9, jobA.getLastBuild().getNumber());

        for (int i = 1; i < 8; i++) {
            j.assertBuildStatusSuccess(jobA.getBuildByNumber(i));
        }
        printThreadsWithName("ActiveMQ.*Task-.*");
        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("ActiveMQ.*Task- count", 1, getCurrentThreadCountForName("ActiveMQ.*Task-.*"));
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testPipelineInvalidProvider() throws Exception {
        _testPipelineInvalidProvider();
    }

    @Test
    public void testSimpleCIEventWithMessagePropertiesAsVariable() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));
        jobA.getBuildersList().add(new Shell("echo TEST_PROP1 = $TEST_PROP1"));
        jobA.getBuildersList().add(new Shell("echo TEST_PROP2 = $TEST_PROP2"));
        attachTrigger(
                new CIBuildTrigger(true, Collections
                        .singletonList(getSubscriberProviderData("otopic", "CI_MESSAGE", "CI_STATUS = 'failed'"))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition("MESSAGE_PROPERTIES",
                "CI_STATUS = failed\nTEST_PROP1 = GOT 1\nTEST_PROP2 = GOT 2", "")));
        jobB.getBuildersList()
                .add(new CIMessageBuilder(getPublisherProviderData("otopic", "${MESSAGE_PROPERTIES}", "")));
        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        FreeStyleBuild lastBuild = jobA.getLastBuild();
        j.assertBuildStatusSuccess(lastBuild);

        j.assertLogContains("echo CI_STATUS = failed", lastBuild);
        j.assertLogContains("echo TEST_PROP1 = GOT 1", lastBuild);
        j.assertLogContains("echo TEST_PROP2 = GOT 2", lastBuild);

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testTTL() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo $CI_MESSAGE_HEADERS"));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(getSubscriberProviderData(null, null, "CI_STATUS = 'failed'"))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(null, "CI_STATUS = failed", null)));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("\"JMSExpiration\":0", jobA.getLastBuild());

        jobB.getPublishersList().clear();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(DEFAULT_PROVIDER_NAME, null, "CI_STATUS = failed", null, 10000)));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogNotContains("JMSExpiration: 0", jobA.getLastBuild());
        j.assertLogContains("\"JMSExpiration\":", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testCITriggerWithFileParameter() throws Exception {
        _testCITriggerWithFileParameter(List.of("CI_MESSAGE", "CI_MESSAGE_HEADERS"));
    }

    @Test
    public void testWaitForCIMessageStepWithFiles() throws Exception {
        _testWaitForCIMessageStepWithFiles(List.of("CI_MESSAGE", "CI_MESSAGE_HEADERS"));
    }

    @Test
    public void testWaitForCIMessagePipelineWithFiles() throws Exception {
        _testWaitForCIMessagePipelineWithFiles(List.of("CI_MESSAGE", "CI_MESSAGE_HEADERS"));
    }
}
