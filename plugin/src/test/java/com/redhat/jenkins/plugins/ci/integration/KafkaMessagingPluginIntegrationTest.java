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
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.kafka.NoneAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.integration.fixtures.KafkaContainer;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

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
                kafka.getBootstrapServersProperty(), kafka.getBootstrapServersProperty(),
                new NoneAuthenticationMethod())));

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
    public List<String> getFileNames() {
        return List.of(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_NAME + "_RECORD");
    }

    @Override
    public String getContainerId() {
        return kafka.getCid();
    }

    public ProviderData getSubscriberProviderData(String provider, String topic, String content) {
        return getSubscriberProviderData(provider, topic, DEFAULT_VARIABLE_NAME, false);
    }

    @Override
    public ProviderData getSubscriberProviderData(String provider, String topic, String variableName, Boolean useFiles,
            MsgCheck... msgChecks) {
        return new KafkaSubscriberProviderData(provider, overrideTopic(topic), "group.id=" + topic,
                Arrays.asList(msgChecks), variableName, useFiles, 60);
    }

    @Override
    public ProviderData getPublisherProviderData(String provider, String topic, String content) {
        return new KafkaPublisherProviderData(provider, overrideTopic(topic), "", content, true);
    }

    @Override
    protected boolean additionalWaitForReceiverToBeReadyCheck(String jobname, int occurrences) {
        if ((testName.getMethodName().equals("testWaitForCIMessageStepWithMessageTooLong") && occurrences == 2)
                || (testName.getMethodName().equals("testWaitForCIMessagePipelineWithMessageTooLong")
                        && occurrences == 2)) {
            return true;
        }
        int count = 0;
        for (String s : logger.getMessages()) {
            if (s.contains("Resetting offset for partition")) {
                count++;
            }
        }
        if (count >= occurrences) {
            return true;
        }
        return false;
    }

    @Test
    public void testSimpleCIEventTriggerRecordInEnv() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        // We are only checking that this shows up in the console output.
        jobA.getBuildersList().add(new Shell("echo $" + DEFAULT_VARIABLE_NAME + "_RECORD"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("some irrelevant content")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("\"topic\":\"" + testName.getMethodName() + "\",\"partition\":0", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggered() throws Exception {
        // For Kafka, when the job is enabled *both* messages will be received.
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo BUILD_NUMBER = $BUILD_NUMBER"));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);
        waitForReceiverToBeReady(jobA.getFullName(), 1);
        jobA.disable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(null)));
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
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo BUILD_NUMBER = $BUILD_NUMBER"));
        waitForReceiverToBeReady(jobA.getFullName(), 1);
        jobA.disable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(MESSAGE_CHECK_CONTENT)));
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
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);
        jobA.doDisable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(null)));

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
    public void testEnvVariablesWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData(testName.getMethodName(), "CI_MESSAGE_TEST", false);
        String postStatements = "echo \"CI_MESSAGE_TEST = \" + CI_MESSAGE_TEST  \n"
                + "  if (env.CI_MESSAGE_TEST == null) {\n" + "    error(\"CI_MESSAGE_TEST not set\")\n" + "  }\n"
                + "  echo \"CI_MESSAGE_TEST_RECORD = \" + env.CI_MESSAGE_TEST_RECORD  \n"
                + "  if (env.CI_MESSAGE_TEST_RECORD == null) {\n" + "    error(\"CI_MESSAGE_TEST_RECORD not set\")\n"
                + "  }\n" + "  if (!env.CI_MESSAGE_TEST_RECORD.contains(\"topic\")) {\n"
                + "    error(\"topic not found\")\n" + "  }\n";
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd, null, postStatements), true));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilScheduledBuildCompletes();
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testPipelineJobProperties() throws Exception {
        List<Thread> leftoverFromPreviousRuns = getThreadsByName("CIBuildTrigger.*");
        for (Thread thread : leftoverFromPreviousRuns) {
            thread.interrupt();

            for (int i = 0; getCurrentThreadCountForName(thread.getName()) != 0 && i < 50; i++) {
                Thread.sleep(200);
            }
        }

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");
        jobB.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition("CI_STATUS2", "", "")));
        ProviderData pd = getPublisherProviderData(MESSAGE_CHECK_CONTENT);
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));

        // [expectedValue: number + '0.0234', field: 'CI_STATUS2']
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "receive");
        jobA.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition(DEFAULT_VARIABLE_NAME, "", "")));
        pd = getSubscriberProviderData();
        CIBuildTrigger t = new CIBuildTrigger(false, Collections.singletonList(pd));
        JobPropertyStep s = new JobPropertyStep(
                Collections.singletonList(new PipelineTriggersJobProperty(Collections.singletonList(t))));
        jobA.setDefinition(new CpsFlowDefinition("def number = currentBuild.getNumber().toString()\n"
                + Snippetizer.object2Groovy(s) + "\nnode('built-in') {\n sleep 1\n}", true));

        j.buildAndAssertSuccess(jobA);
        waitForReceiverToBeReady(jobA.getDisplayName());
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        j.configRoundtrip(jobA);

        j.buildAndAssertSuccess(jobA);
        waitForReceiverToBeReady(jobA.getDisplayName(), 3, true);

        // Wait for threads to disappear.
        for (int i = 0; i < 60 && getCurrentThreadCountForName("CIBuildTrigger.*") > 1; i++) {
            Thread.sleep(500);
        }
        printThreadsWithName("CIBuildTrigger.*");
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

        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        pd = getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE));
        t = new CIBuildTrigger(false, Collections.singletonList(pd));
        s = new JobPropertyStep(
                Collections.singletonList(new PipelineTriggersJobProperty(Collections.singletonList(t))));
        jobA.setDefinition(new CpsFlowDefinition("def number = currentBuild.getNumber().toString()\n"
                + Snippetizer.object2Groovy(s) + "\nnode('built-in') {\n sleep 1\n}", false));
        scheduleAwaitStep(jobA, 8, true);

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
        printThreadsWithName("CIBuildTrigger.*");
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        jobA.delete();
        jobB.delete();
    }
}
