package com.redhat.jenkins.plugins.ci.integration;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.util.Collections.singleton;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.StringParameter;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Inject;
import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.FedmsgRelayContainer;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger;
import com.redhat.jenkins.plugins.ci.integration.po.CINotifierPostBuildStep;
import com.redhat.jenkins.plugins.ci.integration.po.CISubscriberBuildStep;
import com.redhat.jenkins.plugins.ci.integration.po.FedMsgMessagingProvider;
import com.redhat.jenkins.plugins.ci.integration.po.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;

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
public class FedMsgMessagingPluginIntegrationTest extends AbstractJUnitTest {
    @Inject private DockerContainerHolder<FedmsgRelayContainer> docker;

    private FedmsgRelayContainer fedmsgRelay = null;

    @Before public void setUp() throws Exception {
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
                        //+ " fedmsg-logger --message=\\\"This is a message.\\\"");
                        + " fedmsg-logger "
                        + " \"$@\""
        );
        //+ "--message=\\\'{\\\"compose\\\": "
        //+ "{\\\"compose_id\\\": \\\"This is a message.\\\"}}\\\' --json-input");
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

    private String stringFrom(Process proc) throws InterruptedException, IOException {
        assertThat(proc.waitFor(), is(equalTo(0)));
        StringWriter writer = new StringWriter();
        IOUtils.copy(proc.getInputStream(), writer);
        String string = writer.toString();
        writer.close();
        return string;
    }

    private Process logProcessBuilderIssues(ProcessBuilder pb, String commandName) throws InterruptedException, IOException {
        String dir = "";
        if (pb.directory() != null) {
            dir = pb.directory().getAbsolutePath();
        }
        System.out.println("Running : " + pb.command() + " => directory: " + dir);
        Process processToRun = pb.start();
        int result = processToRun.waitFor();
        if (result != 0) {
            StringWriter writer = new StringWriter();
            IOUtils.copy(processToRun.getErrorStream(), writer);
            System.out.println("Issue occurred during command \"" + commandName + "\":\n" + writer.toString());
            writer.close();
        }
        return processToRun;
    }

    @Test
    public void testSimpleCIEventSubscribe() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.selector.set("CI_TYPE = 'code-quality-checks-done'");
        subscriber.variable.set("HELLO");

        jobA.addShellStep("echo $HELLO");
        jobA.save();
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Hello World");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("Hello World"));
    }

    @Test
    public void testSimpleCIEventSubscribeWithTopicOverride() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.overrides.check();
        subscriber.topic.set("otopic");
        subscriber.selector.set("CI_TYPE = 'code-quality-checks-done'");
        subscriber.variable.set("HELLO");

        jobA.addShellStep("echo $HELLO");
        jobA.save();
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("otopic");
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Hello World");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("Hello World"));
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithPipelineSendMsg() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();

        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);
        job.script.set("node('master') {\n sendCIMessage " +
                " providerName: 'test', " +
                " messageContent: '', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}");
        job.save();
        job.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();

    }

    @Test
    public void testAddDuplicateMessageProvider() throws Exception {
        jenkins.configure();
        elasticSleep(5000);
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        FedMsgMessagingProvider msgConfig = new FedMsgMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .topic("tom")
                .hubAddr("tcp://127.0.0.1:4001")
                .pubAddr("tcp://127.0.0.1:2003");
        jenkins.save();
        assertThat(driver, hasContent("Attempt to add a duplicate JMS Message Provider - test"));
    }

    @Test
    public void testSimpleCIEventTrigger() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();
        // Allow for connection
        elasticSleep(5000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    @Test
    public void testSimpleCIEventTriggerWithCheck() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        CIEventTrigger.MsgCheck check = ciEvent.addMsgCheck();
        check.expectedValue.set("Catch me");
        check.field.set(JMSMessagingWorker.MESSAGECONTENTFIELD);
        jobA.save();
        // Allow for connection
        elasticSleep(5000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Catch me");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    @Test
    public void testSimpleCIEventTriggerWithRegExpCheck() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        jobA.addShellStep("echo CI_MESSAGE = $CI_MESSAGE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        CIEventTrigger.MsgCheck check = ciEvent.addMsgCheck();
        check.expectedValue.set(".+compose_id.+Fedora-Atomic.+");
        check.field.set("compose");
        jobA.save();
        // Allow for connection
        elasticSleep(5000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed\n " +
                "compose = \"compose_id\": \"Fedora-Atomic-25-20170105.0\"");
        notifier.messageContent.set("");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));

    }

    @Test
    public void testSimpleCIEventTriggerWithWildcardInSelector() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("compose LIKE '%compose_id\": \"Fedora-Atomic%'");
        jobA.save();
        // Allow for connection
        elasticSleep(5000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed\n " +
                "compose = \"compose_id\": \"Fedora-Atomic-25-20170105.0\"");
        notifier.messageContent.set("");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    @Test
    public void testSimpleCIEventTriggerWithParamOverride() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done'");
        StringParameter ciStatusParam = jobA.addParameter(StringParameter.class);
        ciStatusParam.setName("PARAMETER");
        ciStatusParam.setDefault("bad parameter value");

        jobA.addShellStep("echo $PARAMETER");
        jobA.addShellStep("echo $CI_MESSAGE");
        jobA.save();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);

        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("PARAMETER = my parameter");
        notifier.messageContent.set("This is my content");

        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("my parameter"));
        assertThat(jobA.getLastBuild().getConsole(), containsString("This is my content"));
    }

    @Test
    public void testSimpleCIEventTriggerWithTopicOverride() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.overrides.check();
        ciEvent.topic.set("otopic");
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();
        // Allow for connection
        elasticSleep(5000);

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.overrides.check();
        notifier.topic.set("otopic");
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventSendAndWaitPipeline() throws Exception {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage providerName: 'test'," +
                "selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"  \necho \"scott = \" + scott}");
        wait.save();
        wait.startBuild();

        WorkflowJob send = jenkins.jobs.create(WorkflowJob.class);
        send.script.set("node('master') {\n sendCIMessage" +
                " providerName: 'test', " +
                " messageContent: 'abcdefg', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}");
        send.save();
        send.startBuild().shouldSucceed();
        send.getLastBuild().getConsole().contains("scott = abcdefg");

        elasticSleep(1000);
        wait.getLastBuild().shouldSucceed();

    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testSimpleCIEventTriggerWithPipelineWaitForMsg() throws Exception {
        WorkflowJob wait = jenkins.jobs.create(WorkflowJob.class);
        wait.script.set("node('master') {\n def scott = waitForCIMessage  providerName: 'test', " +
                " selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"  \necho \"scott = \" + scott}");
        wait.save();
        wait.startBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        notifier.messageContent.set("Hello World");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        wait.getLastBuild().shouldSucceed();
        assertThat(wait.getLastBuild().getConsole(), containsString("Hello World"));
    }

    @Test
    public void testSimpleCIEventTriggerHeadersInEnv() throws Exception, InterruptedException {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done'");

        // We are only checking that this shows up in the console output.
        jobA.addShellStep("echo $MESSAGE_HEADERS");
        jobA.save();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);

        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageContent.set("some irrelevant content");

        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("{\"topic\":\"org.fedoraproject\"}"));
    }

    @Test
    public void testSimpleCIEventSubscribeWithNoParamOverride() throws Exception, InterruptedException {
        // Job parameters are NOT overridden when the subscribe build step is used.
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();

        StringParameter ciStatusParam = jobA.addParameter(StringParameter.class);
        ciStatusParam.setName("PARAMETER");
        ciStatusParam.setDefault("original parameter value");

        CISubscriberBuildStep subscriber = jobA.addBuildStep(CISubscriberBuildStep.class);
        subscriber.selector.set("CI_TYPE = 'code-quality-checks-done'");
        subscriber.variable.set("MESSAGE_CONTENT");

        jobA.addShellStep("echo $PARAMETER");
        jobA.addShellStep("echo $MESSAGE_CONTENT");
        jobA.save();
        elasticSleep(1000);
        jobA.scheduleBuild();

        FreeStyleJob jobB = jenkins.jobs.create();
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);

        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("PARAMETER = my parameter");
        notifier.messageContent.set("This is my content");

        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        jobA.getLastBuild().shouldSucceed().shouldExist();
        assertThat(jobA.getLastBuild().getConsole(), containsString("original parameter value"));
        assertThat(jobA.getLastBuild().getConsole(), containsString("This is my content"));
    }
}
