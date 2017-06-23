package com.redhat.jenkins.plugins.ci.integration;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.util.Collections.singleton;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.inject.Inject;
import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.FedmsgRelayContainer;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger;
import com.redhat.jenkins.plugins.ci.integration.po.FedMsgMessagingProvider;
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
public class FedMsgMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {
    @Inject private DockerContainerHolder<FedmsgRelayContainer> docker;

    private FedmsgRelayContainer fedmsgRelay = null;

    @Test
    public void testGlobalConfigTestConnection() throws Exception {
    }

    @Test
    public void testAddDuplicateMessageProvider() throws Exception {
        jenkins.configure();
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        FedMsgMessagingProvider msgConfig = new FedMsgMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .topic("tom")
                .hubAddr("tcp://127.0.0.1:4001")
                .pubAddr("tcp://127.0.0.1:2003");
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
        String expected = "{\"topic\":\"org.fedoraproject\"}";
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
        String expected = "scott = {\"CI_TYPE\":\"code-quality-checks-done\"," +
                "\"message-content\":\"abcdefg\",\"CI_STATUS\":\"failed\",\"CI_NAME\":\"" +
                send.name +
                "\",\"topic\":\"org.fedoraproject\"}";
        _testSimpleCIEventSendAndWaitPipeline(send, expected);
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        String expected = "scott = {\"CI_TYPE\":\"code-quality-checks-done\"" +
                ",\"message-content\":\"abcdefg\",\"CI_STATUS\":\"failed\",\"CI_NAME\":\"" + send.name +
                "\",\"topic\":\"org.fedoraproject.my-topic\"}";
        String selector = "topic = '";
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

    @Ignore("failonError does not work in FedMsg/ZMQ")
    @Test
    public void testEnsureFailedSendingOfMessageFailsBuild() throws Exception {
        // failonError does not work in FedMsg
    }

    @Ignore("failonError does not work in FedMsg/ZMQ")
    @Test
    public void testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
    }

    @WithPlugins({"workflow-aggregator", "monitoring"})
    @Test
    public void testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        _testAbortWaitingForMessageWithPipelineBuild();
    }

    @Before
    public void setUp() throws Exception {
        fedmsgRelay = docker.get();
        jenkins.configure();
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        FedMsgMessagingProvider msgConfig = new FedMsgMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .topic("org.fedoraproject")
                .hubAddr(fedmsgRelay.getHub())
                .pubAddr(fedmsgRelay.getPublisher());
        jenkins.save();
    }

    @Test
    public void testTriggeringUsingFedMsgLoggerAndSingleQuotes() throws Exception {
        testTriggeringUsingFedMsgLogger("topic = 'org.fedoraproject.dev.logger.log'");
    }

    @Test
    public void testTriggeringUsingFedMsgLoggerAndDoubleQuotes() throws Exception {
        testTriggeringUsingFedMsgLogger("topic = \"org.fedoraproject.dev.logger.log\"");
    }

    public void testTriggeringUsingFedMsgLogger(String topic) throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_MESSAGE = $CI_MESSAGE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set(topic);
        CIEventTrigger.MsgCheck check = ciEvent.addMsgCheck();
        check.expectedValue.set(".+compose_id.+message.+");
        check.field.set("compose");
        jobA.save();
        // Allow for connection
        elasticSleep(5000);

        File privateKey = File.createTempFile("ssh", "key");
        FileUtils.copyURLToFile(
                FedmsgRelayContainer.class
                        .getResource("FedmsgRelayContainer/unsafe"), privateKey);
        Files.setPosixFilePermissions(privateKey.toPath(), singleton(OWNER_READ));

        File ssh = File.createTempFile("jenkins", "ssh");
        FileUtils.writeStringToFile(ssh,
                "#!/bin/sh\n" +
                        "exec ssh -o StrictHostKeyChecking=no -i "
                        + privateKey.getAbsolutePath()
                        + " fedmsg2@" + fedmsgRelay.getIpAddress()
                        + " fedmsg-logger "
                        + " \"$@\""
        );
        Files.setPosixFilePermissions(ssh.toPath(),
                new HashSet<>(Arrays.asList(OWNER_READ, OWNER_EXECUTE)));

        System.out.println(FileUtils.readFileToString(ssh));
        ProcessBuilder gitLog1Pb = new ProcessBuilder(ssh.getAbsolutePath(),
                "--message='{\"compose\": "
                        + "{\"compose_id\": \"This is a message.\"}}\'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb,
                "ssh"));
        System.out.println(output);

        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("This is a message"));
    }

    @WithPlugins({"workflow-aggregator", "monitoring", "dumpling"})
    @Test
    public void testPipelineJobProperties() throws Exception {
        WorkflowJob workflowJob = jenkins.jobs.create(WorkflowJob.class);
        workflowJob.script.set("properties(\n" +
                "        [\n" +
                "                pipelineTriggers(\n" +
                "  [[$class: 'CIBuildTrigger', checks: [], providerName: 'test', selector: 'topic = \"org.fedoraproject.dev.logger.log\"']]\n" +
                "                )\n" +
                "        ]\n" +
                ")\nnode('master') {\n sleep 1\n}");
        workflowJob.save();
        workflowJob.startBuild();
        workflowJob.configure();
        workflowJob.save();
        workflowJob.startBuild();
        elasticSleep(2000);
        printThreadsWithName("iothread-");
        printThreadsWithName("CIBuildTrigger");
        int ioCount = getCurrentThreadCountForName("iothread-");
        assertTrue("iothread-2 count is not 1", ioCount == 1);
        int triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is not 1", triggers == 1);

        System.out.println("Starting rapid fire - no property changes.");
        String message = "{ \"CI_STATUS\": \"failed\" }";
        for (int i = 1 ; i <= 3 ; i++) {
            sendFedMsgMessageUsingLogger(message);
        }
        System.out.println("DONE rapid fire - no property changes.");

        elasticSleep(2000);
        assertTrue("there are not 5 builds", workflowJob.getLastBuild().getNumber() == 5);

        ioCount = getCurrentThreadCountForName("iothread-");
        assertTrue("iothread-2 count is not 1", ioCount == 1);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is not 1", triggers == 1);

        workflowJob.configure();
        workflowJob.script.set("def d = new Double(Math.random())\n" +
                "def r = d + 1.0\n" +
                "\n" +
                "properties(\n" +
                "        [\n" +
                "                pipelineTriggers(\n" +
                "  [[$class: 'CIBuildTrigger', checks: [], providerName: 'test', selector: 'CI_STATUS between 0 and ' + r.toString() + '\\'']]\n" +
                "                )\n" +
                "        ]\n" +
                ")\n" +
                "\n" +
                "node() {\n" +
                "    echo \"hi\"\n" +
                "}");
        workflowJob.sandbox.check(false);
        workflowJob.save();
        workflowJob.startBuild();
        elasticSleep(5000);

        Double randomStatus = Math.random();
        String message2 = "{ \"CI_STATUS\": " + randomStatus.toString() +" }";
        System.out.println("Starting rapid fire - WITH property changes.");
        for (int i = 1 ; i <= 3 ; i++) {
            sendFedMsgMessageUsingLogger(message2);
            elasticSleep(1000);
        }

        System.out.println("DONE rapid fire - WITH property changes.");
        elasticSleep(5000);
        assertTrue("there are not 9 builds", workflowJob.getLastBuild().getNumber() == 9);

        for (int i = 1 ; i <= 9 ; i++) {
            Build b1 = new Build(workflowJob, i);
            assertTrue(b1.isSuccess());
        }
        printThreadsWithName("iothread-");
        printThreadsWithName("CIBuildTrigger");
        ioCount = getCurrentThreadCountForName("iothread-");
        assertTrue("iothread-2 count is not 1", ioCount == 1);
        triggers = getCurrentThreadCountForName("CIBuildTrigger");
        assertTrue("CIBuildTrigger count is not 1", triggers == 1);
    }

    private void sendFedMsgMessageUsingLogger(String message) throws Exception {
        File privateKey = File.createTempFile("ssh", "key");
        FileUtils.copyURLToFile(
                FedmsgRelayContainer.class
                        .getResource("FedmsgRelayContainer/unsafe"), privateKey);
        Files.setPosixFilePermissions(privateKey.toPath(), singleton(OWNER_READ));

        File ssh = File.createTempFile("jenkins", "ssh");
        FileUtils.writeStringToFile(ssh,
                "#!/bin/sh\n" +
                        "exec ssh -o StrictHostKeyChecking=no -i "
                        + privateKey.getAbsolutePath()
                        + " fedmsg2@" + fedmsgRelay.getIpAddress()
                        + " fedmsg-logger "
                        + " \"$@\""
        );
        Files.setPosixFilePermissions(ssh.toPath(),
                new HashSet<>(Arrays.asList(OWNER_READ, OWNER_EXECUTE)));

        System.out.println(FileUtils.readFileToString(ssh));
        ProcessBuilder gitLog1Pb = new ProcessBuilder(ssh.getAbsolutePath(),
                "--message=\'" + message + "\'",
//                "--message='{\"compose\": "
//                        + "{\"compose_id\": \"This is a message.\"}}\'",
                "--json-input"
        );
        String output = stringFrom(logProcessBuilderIssues(gitLog1Pb,
                "ssh"));
        System.out.println(output);

    }
}
