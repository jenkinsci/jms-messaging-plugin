package com.redhat.jenkins.plugins.ci.integration;

import static java.util.Collections.singletonMap;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.StringParameter;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Inject;
import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.JBossAMQContainer;
import com.redhat.jenkins.plugins.ci.integration.po.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.integration.po.GlobalCIConfiguration;

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
@WithPlugins("jms-messaging")
@WithDocker
public class AmqMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {
    @Inject private DockerContainerHolder<JBossAMQContainer> docker;

    private JBossAMQContainer amq = null;
    private static final int INIT_WAIT = 360;

    @Test
    public void testGlobalConfigTestConnection() throws Exception {
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
    public void testSimpleCIEventSubscribe() throws Exception {
        _testSimpleCIEventSubscribe();
    }

    @Test
    public void testSimpleCIEventSubscribeWithTopicOverride() throws Exception, InterruptedException {
        _testSimpleCIEventSubscribeWithTopicOverride();
    }

    @Test
    public void testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic();
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithPipelineSendMsg() throws Exception {
        _testSimpleCIEventTriggerWithPipelineSendMsg();
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
    public void testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic() throws Exception {
        _testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic();
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
        expected += "\",\"CI_TYPE\":\"code-quality-checks-done\"}";

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
    public void testSimpleCIEventTriggerWithPipelineWaitForMsg() throws Exception {
        _testSimpleCIEventTriggerWithPipelineWaitForMsg();
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
    public void testDisabledJobDoesNotGetTriggered() throws Exception {
        _testDisabledJobDoesNotGetTriggered();
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
    public void testPipelineJobProperties() throws Exception {
        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        send.configure();
        StringParameter ciStatusParam = send.addParameter(StringParameter.class);
        ciStatusParam.setName("CI_STATUS2");
        ciStatusParam.setDefault("");
        send.script.set("node('master') {\n sendCIMessage" +
                " providerName: 'test', " +
                " failOnError: true, " +
                " messageContent: 'abcdefg', " +
                " messageProperties: 'CI_STATUS2 = ${CI_STATUS2}', " +
                " messageType: 'CodeQualityChecksDone'}");
        send.save();

        //[expectedValue: number + '0.0234', field: 'CI_STATUS2']
        WorkflowJob workflowJob = jenkins.jobs.create(WorkflowJob.class);
        workflowJob.script.set("def number = currentBuild.getNumber().toString()\n" +
                "properties(\n" +
                "        [\n" +
                "                pipelineTriggers(\n" +
                "  [[$class: 'CIBuildTrigger', checks: [], providerName: 'test', selector: 'CI_NAME = \\'" + send.name + "\\'']]\n" +
                "                )\n" +
                "        ]\n" +
                ")\nnode('master') {\n sleep 1\n}");
        workflowJob.save();
        workflowJob.startBuild();
        workflowJob.configure();
        workflowJob.save();
        workflowJob.startBuild();
        elasticSleep(2000);
        printThreadsWithName("ActiveMQ.*Task-");
        printThreadsWithName("CIBuildTrigger");
        int ioCount = getCurrentThreadCountForName("ActiveMQ.*Task-");
        assertTrue("ActiveMQ.*Task- count is not 1", ioCount == 1);
        int triggers = getCurrentThreadCountForName("CIBuildTrigger");
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

        workflowJob.configure();
        workflowJob.script.set("def number = currentBuild.getNumber().toString()\n" +
                "properties(\n" +
                "        [\n" +
                "                pipelineTriggers(\n" +
                "  [[$class: 'CIBuildTrigger', checks: [[expectedValue: '.*' + number + '.*', field: 'CI_STATUS2']], providerName: 'test', selector: 'CI_NAME = \\'" + send.name + "\\'']]\n" +
                "                )\n" +
                "        ]\n" +
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

}
