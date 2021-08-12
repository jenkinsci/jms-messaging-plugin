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
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.activemq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.integration.fixtures.ActiveMQContainer;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.MessageUtils;
import hudson.Functions;
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

public class AmqMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {

    @ClassRule
    public static DockerClassRule<ActiveMQContainer> docker = new DockerClassRule<>(ActiveMQContainer.class);
    private static ActiveMQContainer amq = null;

    @BeforeClass
    public static void startBroker() throws Exception {
        assumeFalse(Functions.isWindows());
        amq = docker.create();
    }

    @Before
    public void setUp() throws Exception {

        GlobalCIConfiguration gcc = GlobalCIConfiguration.get();
        gcc.setConfigs(Collections.singletonList(new ActiveMqMessagingProvider(
                DEFAULT_PROVIDER_NAME,
                amq.getBroker(),
                true,
                DEFAULT_TOPIC_NAME,
                null,
                new UsernameAuthenticationMethod("admin", Secret.fromString("redhat"))
        )));

        // TODO test connection
    }

    @Override
    public ProviderData getSubscriberProviderData(String topic, String variableName, String selector, MsgCheck... msgChecks) {
        return new ActiveMQSubscriberProviderData(
                DEFAULT_PROVIDER_NAME,
                overrideTopic(topic),
                selector,//Util.fixNull(selector),
                Arrays.asList(msgChecks),
                variableName,
                60
        );
    }

    @Override
    public ProviderData getPublisherProviderData(String topic, MessageUtils.MESSAGE_TYPE type, String properties, String content) {
        return new ActiveMQPublisherProviderData(
                DEFAULT_PROVIDER_NAME, overrideTopic(topic), type, properties, content, true
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
    public void testSimpleCIEventTriggerWithBooleanParam() throws Exception {
        _testSimpleCIEventTriggerWithBoolParam("scott=123\ntom=456\ndryrun=true", "{ \"scott\": \"123\", \"tom\": \"456\", \"dryrun\": true }",
                "dryrun is true, scott is 123");
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
    public void testSimpleCIEventSubscribeWithTopicOverride() throws Exception, InterruptedException {
        _testSimpleCIEventSubscribeWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverride() throws Exception, InterruptedException {
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
    public void testSimpleCIEventTriggerHeadersInEnv() throws Exception, InterruptedException {
        FreeStyleProject jobB = j.createFreeStyleProject();
        String expected = "{\"CI_STATUS\":\"passed\",\"CI_NAME\":\"" + jobB.getName() + "\",\"CI_TYPE\":\"code-quality-checks-done\"";
        _testSimpleCIEventTriggerHeadersInEnv(jobB, expected);
    }

    @Test
    public void testSimpleCIEventSubscribeWithNoParamOverride() throws Exception, InterruptedException {
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
        stopContainer(amq);
        System.out.println("Waiting 30 secs");
        Thread.sleep(30000);
        _testEnsureFailedSendingOfMessageFailsBuild();
    }

    @Test
    public void testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
        stopContainer(amq);
        System.out.println("Waiting 30 secs");
        Thread.sleep(30000);
        _testEnsureFailedSendingOfMessageFailsPipelineBuild();
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
    public void testPipelineJobPropertiesSingleProvider() throws Exception {
        // For backward compatibility, uses "providerData".
        _testPipelineJobProperties(true);
    }


    @Test
    public void testPipelineJobPropertiesMultipleProviders() throws Exception {
        _testPipelineJobProperties(false);
    }

    public void _testPipelineJobProperties(boolean backwardCompatible) throws Exception {
        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "send");
        send.setDefinition(new CpsFlowDefinition(
                "node('master') {\n sendCIMessage" +
                " providerName: 'test', " +
                " failOnError: true, " +
                " messageContent: '" + MESSAGE_CHECK_CONTENT + "', " +
                " messageProperties: 'CI_STATUS2 = ${CI_STATUS2}', " +
                " messageType: 'CodeQualityChecksDone'}", true));

        //[expectedValue: number + '0.0234', field: 'CI_STATUS2']
        String pd = "[$class: 'ActiveMQSubscriberProviderData', name: 'test', selector: 'CI_NAME = \\'" + send.getName() + "\\'']";
        if (backwardCompatible) {
            pd = "providerData: " + pd;
        } else {
            pd = "providerList: [" + pd + "]";
        }
        WorkflowJob workflowJob = j.jenkins.createProject(WorkflowJob.class, "receive");
        workflowJob.addProperty(new ParametersDefinitionProperty(
                new TextParameterDefinition("CI_MESSAGE", "", "")
        ));
        workflowJob.setDefinition(new CpsFlowDefinition(
                "def number = currentBuild.getNumber().toString()\n" +
                "properties(\n" +
                "    [\n" +
                "        pipelineTriggers(\n" +
                "            [[$class: 'CIBuildTrigger', noSquash: false, " + pd + "]]\n" +
                "        )\n" +
                "    ]\n" +
                ")\nnode('master') {\n sleep 1\n}", true));
        workflowJob.save();

        j.buildAndAssertSuccess(workflowJob);
        // Allow some time for trigger thread stop/start.
        Thread.sleep(2000);
        int ioCount = getCurrentThreadCountForName("ActiveMQ.*Task-");
        assertEquals("ActiveMQ.*Task- count is not 1", 1, ioCount);
        int triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertEquals("CIBuildTrigger count is 1", 1, triggers);

        j.configRoundtrip(workflowJob);

        j.buildAndAssertSuccess(workflowJob);
        Thread.sleep(2000);
        printThreadsWithName("ActiveMQ.*Task-");
        printThreadsWithName("CIBuildTrigger");
        ioCount = getCurrentThreadCountForName("ActiveMQ.*Task-");
        assertEquals("ActiveMQ.*Task- count is not 1", 1, ioCount);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertEquals("CIBuildTrigger count is 1", 1, triggers);

        //checks: [[expectedValue: '0.0234', field: 'CI_STATUS2']]
        String randomNumber = "123456789";
        for (int i = 0; i < 3; i++) {
            QueueTaskFuture<WorkflowRun> build = send.scheduleBuild2(0, new ParametersAction(new TextParameterValue("CI_STATUS2", randomNumber, "")));
            j.assertBuildStatusSuccess(build);
        }

        Thread.sleep(5000);
        assertEquals("there are not 5 builds", 5, workflowJob.getLastBuild().getNumber());

        printThreadsWithName("ActiveMQ.*Task-");
        printThreadsWithName("CIBuildTrigger");
        ioCount = getCurrentThreadCountForName("ActiveMQ.*Task-");
        assertEquals("ActiveMQ.*Task- count is not 1", 1, ioCount);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertEquals("CIBuildTrigger count is not 1", 1, triggers);

        pd = "[$class: 'ActiveMQSubscriberProviderData', checks: [[field: '" + MESSAGE_CHECK_FIELD
                + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']], name: 'test', selector: 'CI_NAME = \\'"
                + send.getName() + "\\'']";
        if (backwardCompatible) {
            pd = "providerData: " + pd;
        } else {
            pd = "providerList: [" + pd + "]";
        }
        workflowJob.setDefinition(new CpsFlowDefinition(
                "def number = currentBuild.getNumber().toString()\n" +
                "properties(\n" +
                "    [\n" +
                "        pipelineTriggers(\n" +
                "            [[$class: 'CIBuildTrigger', noSquash: false, " + pd + "]]\n" +
                "        )\n" +
                "    ]\n" +
                ")\nnode('master') {\n sleep 1\n}", false));
        workflowJob.save();
        workflowJob.scheduleBuild2(0);
        Thread.sleep(5000);

        for (int i = 0; i < 3; i++) {
            QueueTaskFuture<WorkflowRun> build = send.scheduleBuild2(0, new ParametersAction(new TextParameterValue("CI_STATUS2", randomNumber, "")));
            j.assertBuildStatusSuccess(build);
            Thread.sleep(1000);
        }

        Thread.sleep(2000);
        assertEquals("there are not 9 builds", 9, workflowJob.getLastBuild().getNumber());

        for (int i = 0; i < 7; i++) {
            j.assertBuildStatusSuccess(workflowJob.getBuildByNumber(i));
        }
        printThreadsWithName("ActiveMQ.*Task-");
        printThreadsWithName("CIBuildTrigger");
        ioCount = getCurrentThreadCountForName("ActiveMQ.*Task-");
        assertEquals("ActiveMQ.*Task- count is not 1", 1, ioCount);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertEquals("CIBuildTrigger count is not 1", 1, triggers);
    }

    @Test
    public void testPipelineInvalidProvider() throws Exception {
        _testPipelineInvalidProvider();
    }

    @Test
    public void testSimpleCIEventWithMessagePropertiesAsVariable() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));
        jobA.getBuildersList().add(new Shell("echo TEST_PROP1 = $TEST_PROP1"));
        jobA.getBuildersList().add(new Shell("echo TEST_PROP2 = $TEST_PROP2"));
        jobA.addTrigger(new CIBuildTrigger(true, Collections.singletonList(
                getSubscriberProviderData("otopic", "CI_MESSAGE", "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'")
        )));
        jobA.getTrigger(CIBuildTrigger.class).start(jobA, true);
        Thread.sleep(1000);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.addProperty(new ParametersDefinitionProperty(
                new TextParameterDefinition("MESSAGE_PROPERTIES", "CI_STATUS = failed\nTEST_PROP1 = GOT 1\nTEST_PROP2 = GOT 2", "")
        ));
        jobB.getBuildersList().add(new CIMessageBuilder(
                getPublisherProviderData("otopic", MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "${MESSAGE_PROPERTIES}", "")
        ));
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        FreeStyleBuild lastBuild = jobA.getLastBuild();
        j.assertBuildStatusSuccess(lastBuild);

        j.assertLogContains("echo CI_TYPE = code-quality-checks-done", lastBuild);
        j.assertLogContains("echo TEST_PROP1 = GOT 1", lastBuild);
        j.assertLogContains("echo TEST_PROP2 = GOT 2", lastBuild);
    }
}
