package com.redhat.jenkins.plugins.ci.integration;

import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.ActiveMQContainer;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Inject;
import com.redhat.jenkins.plugins.ci.integration.po.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger.ProviderData;
import com.redhat.jenkins.plugins.ci.integration.po.CINotifierBuildStep;
import com.redhat.jenkins.plugins.ci.integration.po.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.integration.po.TextParameter;

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
public class AmqMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {
    @Inject private DockerContainerHolder<ActiveMQContainer> docker;

    private ActiveMQContainer amq = null;
    private static final int INIT_WAIT = 360;

    @Test
    public void testGlobalConfigTestConnection() throws Exception {
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testVerifyModelUIPersistence() throws Exception {
        _testVerifyModelUIPersistence();
    }

    @Test
    public void testSimpleCIEventTriggerWithTextArea() {
        _testSimpleCIEventTriggerWithTextArea("scott=123\ntom=456",
                "scott=123\ntom=456");
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithBooleanParam() {
        _testSimpleCIEventTriggerWithBoolParam("scott=123\ntom=456\ndryrun=true", "{ \"scott\": \"123\", \"tom\": \"456\", \"dryrun\": true }",
                "dryrun is true, scott is 123");
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithChoiceParam() {
        _testSimpleCIEventTriggerWithChoiceParam("scott=123", "{}",
                "mychoice is scott");
    }

    @Test
    public void testAddDuplicateMessageProvider() throws IOException {
        jenkins.configure();
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        ActiveMqMessagingProvider msgConfig = new ActiveMqMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .broker(amq.getBroker())
                .topic("CI")
                .userNameAuthentication("admin", "redhat");
        _testAddDuplicateMessageProvider();
    }

    @Test
    public void testAddQueueMessageProvider() throws IOException {
        jenkins.configure();
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        ActiveMqMessagingProvider msgConfig = new ActiveMqMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("queue")
                .broker(amq.getBroker())
                .useQueues(true)
                .topic("CI")
                .userNameAuthentication("admin", "redhat");
        _testAddQueueMessageProvider();
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

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithPipelineSendMsg() throws Exception {
        _testSimpleCIEventTriggerWithPipelineSendMsg();
    }

    @WithPlugins("workflow-aggregator")
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
        FreeStyleJob jobB = jenkins.jobs.create();
        String expected = "{\"CI_STATUS\":\"passed\",\"CI_NAME\":\"";
        expected += jobB.name;
        expected += "\",\"CI_TYPE\":\"code-quality-checks-done\"";

        _testSimpleCIEventTriggerHeadersInEnv(jobB, expected);
    }

    @Test
    public void testSimpleCIEventSubscribeWithNoParamOverride() throws Exception, InterruptedException {
        _testSimpleCIEventSubscribeWithNoParamOverride();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerOnPipelineJob() throws Exception {
        _testSimpleCIEventTriggerOnPipelineJob();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJob() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJob();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithPipelineWaitForMsg();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithSelectorWithCheckWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithSelectorWithCheckWithPipelineWaitForMsg();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventSendAndWaitPipeline() throws Exception {
        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        String expected = "scott = abcdefg";
        _testSimpleCIEventSendAndWaitPipeline(send, expected);
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
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
    public void testEnsureFailedSendingOfMessageFailsBuild() throws Exception {
        stopContainer(amq);
        System.out.println("Waiting 30 secs");
        elasticSleep(30000);
        _testEnsureFailedSendingOfMessageFailsBuild();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
        stopContainer(amq);
        System.out.println("Waiting 30 secs");
        elasticSleep(30000);
        _testEnsureFailedSendingOfMessageFailsPipelineBuild();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        _testAbortWaitingForMessageWithPipelineBuild();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {
        _testSimpleCIEventTriggerOnPipelineJobWithGlobalEnvVarInTopic();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {
        _testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverrideAndRestart() throws Exception {
        _testSimpleCIEventTriggerWithCheckWithTopicOverrideAndRestart();
    }

    @Before
    public void setUp() throws Exception {
        amq = docker.get();
        jenkins.configure();
        elasticSleep(5000);
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        ActiveMqMessagingProvider msgConfig = new ActiveMqMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .broker(amq.getBroker())
                .topic("CI")
                .userNameAuthentication("admin", "redhat");

        int counter = 0;
        boolean connected = false;
        while (counter < INIT_WAIT) {
            try {
                msgConfig.testConnection();
                waitFor(driver, hasContent("Successfully connected to " + amq.getBroker()), 5);
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

    @WithPlugins({"workflow-aggregator", "monitoring", "dumpling"})
    @Test
    public void testPipelineJobPropertiesSingleProvider() throws Exception {
        // For backward compatibility, uses "providerData".
        _testPipelineJobProperties(true);
    }

    @WithPlugins({"workflow-aggregator", "monitoring", "dumpling"})
    @Test
    public void testPipelineJobPropertiesMultipleProviders() throws Exception {
        _testPipelineJobProperties(false);
    }

    public void _testPipelineJobProperties(boolean backwardCompatible) throws Exception {
        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        send.configure();
        send.script.set("node('master') {\n sendCIMessage" +
                " providerName: 'test', " +
                " failOnError: true, " +
                " messageContent: '" + MESSAGE_CHECK_CONTENT + "', " +
                " messageProperties: 'CI_STATUS2 = ${CI_STATUS2}', " +
                " messageType: 'CodeQualityChecksDone'}");
        send.save();

        //[expectedValue: number + '0.0234', field: 'CI_STATUS2']
        String pd = "[$class: 'ActiveMQSubscriberProviderData', name: 'test', selector: 'CI_NAME = \\'" + send.name + "\\'']";
        if (backwardCompatible) {
            pd = "providerData: " + pd;
        } else {
            pd = "providerList: [" + pd + "]";
        }
        WorkflowJob workflowJob = jenkins.jobs.create(WorkflowJob.class);
        TextParameter ciStatusParam = workflowJob.addParameter(TextParameter.class);
        ciStatusParam.setName("CI_MESSAGE");
        ciStatusParam.setDefault("");
        workflowJob.script.set("def number = currentBuild.getNumber().toString()\n" +
                "properties(\n" +
                "    [\n" +
                "        pipelineTriggers(\n" +
                "            [[$class: 'CIBuildTrigger', noSquash: false, " + pd + "]]\n" +
                "        )\n" +
                "    ]\n" +
                ")\nnode('master') {\n sleep 1\n}");
        workflowJob.save();

        workflowJob.startBuild().shouldSucceed();
        // Allow some time for trigger thread stop/start.
        elasticSleep(2000);
        int ioCount = getCurrentThreadCountForName("ActiveMQ.*Task-");
        assertTrue("ActiveMQ.*Task- count is not 1", ioCount == 1);
        int triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is 1", triggers == 1);
        workflowJob.configure();
        workflowJob.save();
        workflowJob.startBuild().shouldSucceed();
        elasticSleep(2000);
        printThreadsWithName("ActiveMQ.*Task-");
        printThreadsWithName("CIBuildTrigger");
        ioCount = getCurrentThreadCountForName("ActiveMQ.*Task-");
        assertTrue("ActiveMQ.*Task- count is not 1", ioCount == 1);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is 1", triggers == 1);

        //checks: [[expectedValue: '0.0234', field: 'CI_STATUS2']]
        String randomNumber = "123456789";
        for (int i = 0 ; i < 3 ; i++) {
            send.startBuild(singletonMap("CI_STATUS2", randomNumber)).shouldSucceed();
        }

        elasticSleep(5000);
        assertTrue("there are not 5 builds", workflowJob.getLastBuild().getNumber() == 5);

        printThreadsWithName("ActiveMQ.*Task-");
        printThreadsWithName("CIBuildTrigger");
        ioCount = getCurrentThreadCountForName("ActiveMQ.*Task-");
        assertTrue("ActiveMQ.*Task- count is not 1", ioCount == 1);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is not 1", triggers == 1);

        pd = "[$class: 'ActiveMQSubscriberProviderData', checks: [[field: '" + MESSAGE_CHECK_FIELD + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']], name: 'test', selector: 'CI_NAME = \\'" + send.name + "\\'']";
        if (backwardCompatible) {
            pd = "providerData: " + pd;
        } else {
            pd = "providerList: [" + pd + "]";
        }
        workflowJob.configure();
        workflowJob.script.set("def number = currentBuild.getNumber().toString()\n" +
                "properties(\n" +
                "    [\n" +
                "        pipelineTriggers(\n" +
                "            [[$class: 'CIBuildTrigger', noSquash: false, " + pd + "]]\n" +
                "        )\n" +
                "    ]\n" +
                ")\nnode('master') {\n sleep 1\n}");
        workflowJob.sandbox.check(false);
        workflowJob.save();
        workflowJob.startBuild();
        elasticSleep(5000);

        for (int i = 0 ; i < 3 ; i++) {
            send.startBuild(singletonMap("CI_STATUS2", randomNumber)).shouldSucceed();
            elasticSleep(1000);
        }

        elasticSleep(2000);
        assertTrue("there are not 9 builds", workflowJob.getLastBuild().getNumber() == 9);

        for (int i = 0 ; i < 7 ; i++) {
            Build b1 = new Build(workflowJob, i+1);
            assertTrue(b1.isSuccess());
        }
        printThreadsWithName("ActiveMQ.*Task-");
        printThreadsWithName("CIBuildTrigger");
        ioCount = getCurrentThreadCountForName("ActiveMQ.*Task-");
        assertTrue("ActiveMQ.*Task- count is not 1", ioCount == 1);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is not 1", triggers == 1);
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testPipelineInvalidProvider() throws Exception {
        _testPipelineInvalidProvider();
    }

    @Test
    public void testSimpleCIEventWithMessagePropertiesAsVariable() {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        jobA.addShellStep("echo TEST_PROP1 = $TEST_PROP1");
        jobA.addShellStep("echo TEST_PROP2 = $TEST_PROP2");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        pd.overrides.check();
        pd.topic.set("otopic");
        jobA.save();
        // Allow for connection
        elasticSleep(1000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        TextParameter param = jobB.addParameter(TextParameter.class);
        param.setName("MESSAGE_PROPERTIES");
        param.setDefault("CI_STATUS = failed\nTEST_PROP1 = GOT 1\nTEST_PROP2 = GOT 2");
        CINotifierBuildStep notifier = jobB.addBuildStep(CINotifierBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("otopic");
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("${MESSAGE_PROPERTIES}");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo TEST_PROP1 = GOT 1"));
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo TEST_PROP2 = GOT 2"));
    }
}
