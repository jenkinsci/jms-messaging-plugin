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
import java.util.logging.Logger;

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
import hudson.model.BooleanParameterDefinition;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.TextParameterDefinition;
import hudson.model.TextParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Shell;

public class AmqMessagingPluginIntegrationTest extends SharedMessagingPluginIntegrationTest {
    private static final Logger log = Logger.getLogger(AmqMessagingPluginIntegrationTest.class.getName());

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
    public List<String> getFileNames() {
        return List.of(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_NAME + "_HEADERS");
    }

    @Override
    public String getContainerId() {
        return amq.getCid();
    }

    @Override
    public ProviderData getSubscriberProviderData(String provider, String topic, String variableName, Boolean useFiles,
            MsgCheck... msgChecks) {
        return getSubscriberProviderData(provider, topic, variableName, useFiles, null, msgChecks);
    }

    public ProviderData getSubscriberProviderData(String provider, String topic, String variableName, Boolean useFiles,
            String selector, MsgCheck... msgChecks) {
        return new ActiveMQSubscriberProviderData(provider, overrideTopic(topic), Util.fixNull(selector),
                Arrays.asList(msgChecks), Util.fixNull(variableName, DEFAULT_VARIABLE_NAME), useFiles, 60);
    }

    public ProviderData getSubscriberProviderDataSelectorOnly(String selector) {
        return getSubscriberProviderData(DEFAULT_PROVIDER_NAME, testName.getMethodName(), DEFAULT_VARIABLE_NAME, false,
                selector);
    }

    @Override
    public ProviderData getPublisherProviderData(String provider, String topic, String content) {
        return getPublisherProviderData(provider, topic, content, null, 0);
    }

    public ProviderData getPublisherProviderData(String provider, String topic, String content, String properties) {
        return getPublisherProviderData(provider, topic, content, properties, 0);
    }

    public ProviderData getPublisherProviderData(String provider, String topic, String content, String properties,
            int ttl) {
        return new ActiveMQPublisherProviderData(provider, overrideTopic(topic), properties, content, true, ttl);
    }

    @Test
    public void testEnvVariablesWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData(DEFAULT_PROVIDER_NAME, testName.getMethodName(), "CI_MESSAGE_TEST",
                false);
        String postStatements = "echo \"CI_MESSAGE_TEST = \" + CI_MESSAGE_TEST  \n"
                + "  if (env.CI_MESSAGE_TEST == null) {\n" + "    error(\"CI_MESSAGE_TEST not set\")\n" + "  }\n"
                + "  echo \"CI_MESSAGE_TEST_HEADERS = \" + env.CI_MESSAGE_TEST_HEADERS  \n"
                + "  if (env.CI_MESSAGE_TEST_HEADERS == null) {\n" + "    error(\"CI_MESSAGE_TEST_HEADERS not set\")\n"
                + "  }\n" + "  if (!env.CI_MESSAGE_TEST_HEADERS.contains(\"TEST_PROPERTY\")) {\n"
                + "    error(\"TEST_PROPERTY not found\")\n" + "  }\n";
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd, null, postStatements), true));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(DEFAULT_PROVIDER_NAME,
                testName.getMethodName(), "Hello World", "TEST_PROPERTY = TEST_VALUE")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testPipelineJobPropertiesMultipleProviders() throws Exception {
        List<Thread> leftoverFromPreviousRuns = getThreadsByName("ActiveMQ.*Task-.*");
        leftoverFromPreviousRuns.addAll(getThreadsByName("CIBuildTrigger.*"));
        for (Thread thread : leftoverFromPreviousRuns) {
            log.severe("Trying to kill thread: " + thread.getName());
            thread.interrupt();

            for (int i = 0; getCurrentThreadCountForName(thread.getName()) != 0 && i < 50; i++) {
                log.severe("    sleeping...");
                Thread.sleep(200);
            }
        }
        log.severe("Number of threads: " + getCurrentThreadCountForName("ActiveMQ.*Task-.*"));
        assertEquals("ActiveMQ.*Task- count", 0, getCurrentThreadCountForName("ActiveMQ.*Task-.*"));
        assertEquals("CIBuildTrigger count", 0, getCurrentThreadCountForName("CIBuildTrigger.*"));

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");
        jobB.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition("CI_STATUS2", "", "")));
        ProviderData pd = getPublisherProviderData(DEFAULT_PROVIDER_NAME, testName.getMethodName(),
                MESSAGE_CHECK_CONTENT, "CI_STATUS2 = ${CI_STATUS2}");
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));

        // [expectedValue: number + '0.0234', field: 'CI_STATUS2']
        pd = getSubscriberProviderData();
        CIBuildTrigger t = new CIBuildTrigger(false, Collections.singletonList(pd));
        JobPropertyStep s = new JobPropertyStep(
                Collections.singletonList(new PipelineTriggersJobProperty(Collections.singletonList(t))));
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "receive");
        jobA.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition(DEFAULT_VARIABLE_NAME, "", "")));
        jobA.setDefinition(new CpsFlowDefinition("def number = currentBuild.getNumber().toString()\n"
                + Snippetizer.object2Groovy(s) + "\nnode('built-in') {\n sleep 1\n}", true));

        j.buildAndAssertSuccess(jobA);
        waitForReceiverToBeReady(jobA.getDisplayName());
        assertEquals("ActiveMQ.*Task- count", 1, getCurrentThreadCountForName("ActiveMQ.*Task-.*"));
        assertEquals("CIBuildTrigger count", 1, getCurrentThreadCountForName("CIBuildTrigger.*"));

        j.configRoundtrip(jobA);

        j.buildAndAssertSuccess(jobA);
        waitForReceiverToBeReady(jobA.getDisplayName(), 3);

        // Wait for threads to disappear.
        for (int i = 0; i < 60 && getCurrentThreadCountForName("ActiveMQ.*Task-.*") > 1; i++) {
            Thread.sleep(500);
        }
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

        pd = getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE));
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
    public void testSimpleCIEventWithMessagePropertiesAsVariable() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));
        jobA.getBuildersList().add(new Shell("echo TEST_PROP1 = $TEST_PROP1"));
        jobA.getBuildersList().add(new Shell("echo TEST_PROP2 = $TEST_PROP2"));
        attachTrigger(new CIBuildTrigger(true, Collections.singletonList(getSubscriberProviderData("otopic"))), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition("MESSAGE_PROPERTIES",
                "CI_STATUS = failed\nTEST_PROP1 = GOT 1\nTEST_PROP2 = GOT 2", "")));
        jobB.getBuildersList().add(new CIMessageBuilder(
                getPublisherProviderData(DEFAULT_PROVIDER_NAME, "otopic", "", "${MESSAGE_PROPERTIES}")));
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
        jobA.getBuildersList().add(new Shell("echo $" + DEFAULT_VARIABLE_NAME + "_HEADERS"));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(DEFAULT_PROVIDER_NAME, testName.getMethodName(), null, "CI_STATUS = failed")));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("\"JMSExpiration\":0", jobA.getLastBuild());

        jobB.getPublishersList().clear();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(DEFAULT_PROVIDER_NAME,
                testName.getMethodName(), null, "CI_STATUS = failed", 10000)));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogNotContains("JMSExpiration: 0", jobA.getLastBuild());
        j.assertLogContains("\"JMSExpiration\":", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerHeadersInEnv() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(getSubscriberProviderDataSelectorOnly("CI_STATUS = 'passed'"))),
                jobA);

        // We are only checking that this shows up in the console output.
        jobA.getBuildersList().add(new Shell("echo $" + DEFAULT_VARIABLE_NAME + "_HEADERS"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(DEFAULT_PROVIDER_NAME,
                testName.getMethodName(), "some irrelevant content", "CI_STATUS = passed")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("{\"CI_STATUS\":\"passed\",\"CI_NAME\":\"" + jobB.getName() + "\"", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithWildcardInSelector() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(
                        getSubscriberProviderDataSelectorOnly("compose LIKE '%compose_id\": \"Fedora-Atomic%'"))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData(DEFAULT_PROVIDER_NAME, testName.getMethodName(), "",
                        "CI_STATUS = failed\n compose = \"compose_id\": \"Fedora-Atomic-25-20170105.0\"")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_STATUS = failed", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithParamOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(getSubscriberProviderDataSelectorOnly("CI_STATUS = 'failed'"))),
                jobA);

        jobA.addProperty(
                new ParametersDefinitionProperty(new StringParameterDefinition("PARAMETER", "bad parameter value", ""),
                        new StringParameterDefinition("status", "unknown status", "")));
        jobA.getBuildersList().add(new Shell("echo $PARAMETER"));
        jobA.getBuildersList().add(new Shell("echo $" + DEFAULT_VARIABLE_NAME));
        jobA.getBuildersList().add(new Shell("echo status::$status"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(DEFAULT_PROVIDER_NAME,
                testName.getMethodName(), "This is my content with ${COMPOUND} ${BUILD_STATUS}",
                "CI_STATUS = failed\nPARAMETER = my parameter\nstatus=${BUILD_STATUS}\nCOMPOUND = Z${PARAMETER}Z")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        FreeStyleBuild lastBuild = jobA.getLastBuild();
        j.assertBuildStatusSuccess(lastBuild);
        j.assertLogContains("status::SUCCESS", lastBuild);
        j.assertLogContains("my parameter", lastBuild);
        j.assertLogContains("This is my content with Zmy parameterZ SUCCESS", lastBuild);

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithBoolParam() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "foo");
        jobA.setDefinition(
                new CpsFlowDefinition("node('built-in') {\n echo \"dryrun is $dryrun, scott is $scott\"\n}", true));
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(DEFAULT_VARIABLE_NAME, "", ""),
                new BooleanParameterDefinition("dryrun", false, ""), new StringParameterDefinition("scott", "", "")));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData(DEFAULT_PROVIDER_NAME, testName.getMethodName(),
                        "{ \"scott\": \"123\", \"tom\": \"456\", \"dryrun\": true }",
                        "scott=123\ntom=456\ndryrun=true")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("dryrun is true, scott is 123", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }
}
