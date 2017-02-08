package com.redhat.jenkins.plugins.ci.integration;

import com.google.inject.Inject;
import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.JBossAMQContainer;
import com.redhat.jenkins.plugins.ci.integration.po.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger;
import com.redhat.jenkins.plugins.ci.integration.po.CINotifierPostBuildStep;
import com.redhat.jenkins.plugins.ci.integration.po.CISubscriberBuildStep;
import com.redhat.jenkins.plugins.ci.integration.po.GlobalCIConfiguration;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.JenkinsLogger;
import org.jenkinsci.test.acceptance.po.StringParameter;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.TimeoutException;

import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;

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
public class AmqMessagingPluginIntegrationTest extends AbstractJUnitTest {
    @Inject private DockerContainerHolder<JBossAMQContainer> docker;

    private JBossAMQContainer amq = null;
    private static final int INIT_WAIT = 360;

    @Before public void setUp() throws Exception {
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

    @Test
    public void testGlobalConfigTestConnection() throws Exception {
    }

    @Test
    public void testAddDuplicateMessageProvider() throws Exception {
        jenkins.configure();
        elasticSleep(5000);
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        ActiveMqMessagingProvider msgConfig = new ActiveMqMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
                .broker(amq.getBroker())
                .topic("CI")
                .userNameAuthentication("admin", "redhat");
        jenkins.save();
        assertThat(driver, hasContent("Attempt to add a duplicate JMS Message Provider - test"));
    }

    @WithPlugins("workflow-aggregator@1.2")
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

    @WithPlugins("workflow-aggregator@1.2")
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

    @Test
    public void testJobRename() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();
        elasticSleep(1000);

        jobA.renameTo("ABC");
        elasticSleep(3000);
        assertThat("Trigger not subscribed", isSubscribed("ABC"));
    }

    public boolean isSubscribed(String job) {
        try {
            JenkinsLogger logger = jenkins.getLogger("all");
            logger.waitForLogged(Pattern.compile("Successfully subscribed job \'" +
                    job + "\' to.*"));
            return true;
        } catch (TimeoutException ex) {
            return false;
        }
    }

    @Test
    public void testSimpleCIEventTrigger() throws Exception {
        FreeStyleJob jobA = jenkins.jobs.create();
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();
        elasticSleep(1000);

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
    public void testSimpleCIEventSubscribe() throws Exception, InterruptedException {
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
    public void testSimpleCIEventTriggerWithParamOverride() throws Exception, InterruptedException {
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
