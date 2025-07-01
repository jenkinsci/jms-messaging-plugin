package com.redhat.jenkins.plugins.ci.integration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;

import com.google.common.base.Strings;
import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.CIMessageBuilder;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.CIMessageSubscriberBuilder;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.pipeline.CIMessageSenderStep;
import com.redhat.jenkins.plugins.ci.pipeline.CIMessageSubscriberStep;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

import hudson.model.FileParameterDefinition;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Shell;

/**
 * Created by shebert on 06/06/17.
 */
public abstract class SharedMessagingPluginIntegrationTest extends BaseTest {

    public static String MESSAGE_CHECK_FIELD = "content";
    public static String MESSAGE_CHECK_VALUE = "catch me";
    public static String MESSAGE_CHECK_CONTENT = "{ \"" + MESSAGE_CHECK_FIELD + "\" : \"" + MESSAGE_CHECK_VALUE
            + "\" }";
    public static String DEFAULT_PROVIDER_NAME = "test";
    public static String DEFAULT_TOPIC_NAME = "topic";
    public static Boolean DEFAULT_USE_FILES = false;
    public static String DEFAULT_VARIABLE_NAME = "CI_MESSAGE";

    public abstract List<String> getFileNames();

    public abstract String getContainerId();

    public ProviderData getSubscriberProviderData(MsgCheck... msgChecks) {
        return getSubscriberProviderData(testName.getMethodName(), DEFAULT_VARIABLE_NAME, DEFAULT_USE_FILES, msgChecks);
    }

    public ProviderData getSubscriberProviderData(String topic) {
        return getSubscriberProviderData(topic, DEFAULT_VARIABLE_NAME, DEFAULT_USE_FILES);
    }

    public ProviderData getSubscriberProviderData(String topic, MsgCheck... msgChecks) {
        return getSubscriberProviderData(topic, DEFAULT_VARIABLE_NAME, DEFAULT_USE_FILES, msgChecks);
    }

    public ProviderData getSubscriberProviderData(String topic, String variableName, Boolean useFiles,
            MsgCheck... msgChecks) {
        return getSubscriberProviderData(DEFAULT_PROVIDER_NAME, topic, variableName, useFiles, msgChecks);
    }

    public abstract ProviderData getSubscriberProviderData(String provider, String topic, String variableName,
            Boolean useFiles, MsgCheck... msgChecks);

    public ProviderData getPublisherProviderData(String content) {
        return getPublisherProviderData(testName.getMethodName(), content);
    }

    public ProviderData getPublisherProviderData(String topic, String content) {
        return getPublisherProviderData(DEFAULT_PROVIDER_NAME, topic, content);
    }

    public abstract ProviderData getPublisherProviderData(String provider, String topic, String content);

    public String buildWaitForCIMessageScript(ProviderData pd) {
        return buildWaitForCIMessageScript(pd, null, null);
    }

    public String buildWaitForCIMessageScript(ProviderData pd, String preStatements) {
        return buildWaitForCIMessageScript(pd, preStatements, null);
    }

    public String buildWaitForCIMessageScript(ProviderData pd, String preStatements, String postStatements) {
        StringBuilder sb = new StringBuilder();
        sb.append("node(\"built-in\") {\n");
        if (preStatements != null) {
            sb.append("    " + preStatements + "\n");
        }
        CIMessageSubscriberStep step = new CIMessageSubscriberStep(pd);
        sb.append("    def message = " + Snippetizer.object2Groovy(step) + "\n");
        sb.append("    echo \"message = \" + message\n");
        if (postStatements != null) {
            sb.append("    " + postStatements + "\n");
        }
        sb.append("}");
        return sb.toString();
    }

    public String buildSendCIMessageScript(ProviderData pd) {
        return buildSendCIMessageScript(pd, null);
    }

    public String buildSendCIMessageScript(ProviderData pd, String preStatements) {
        StringBuilder sb = new StringBuilder();
        sb.append("node(\"built-in\") {\n");
        if (preStatements != null) {
            sb.append("    " + preStatements + "\n");
        }
        CIMessageSenderStep step = new CIMessageSenderStep(pd);
        sb.append("    def message = " + Snippetizer.object2Groovy(step) + "\n");
        sb.append("}");
        return sb.toString();
    }

    @Test
    public void testVerifyModelUIPersistence() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n echo 'hello world' \n} ", true));
        CIBuildTrigger trigger = new CIBuildTrigger(true, Collections
                .singletonList(getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE))));
        attachTrigger(trigger, jobA);

        j.configRoundtrip(jobA);

        CIBuildTrigger ciTrigger = jobA.getTriggers().values().stream().filter(t -> t instanceof CIBuildTrigger)
                .map(t -> (CIBuildTrigger) t).findFirst().get();

        assertThat(ciTrigger, equalTo(trigger));

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "jobB");
        ProviderData pd = getPublisherProviderData(MESSAGE_CHECK_CONTENT);
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));
        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventSubscribe() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData()));

        jobA.getBuildersList().add(new Shell("echo $" + DEFAULT_VARIABLE_NAME));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"Hello World\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithDefaultValue() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList().add(new Shell("echo hello $DEFAULTPARAM"));
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(DEFAULT_VARIABLE_NAME, "", ""),
                new StringParameterDefinition("DEFAULTPARAM", "world", "")));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"hello world\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("hello world", jobA.getLastBuild());

        FreeStyleBuild build = jobA
                .scheduleBuild2(0, new ParametersAction(new StringParameterValue("DEFAULTPARAM", "scott", ""))).get();
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("hello scott", build);

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithTextArea() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList()
                .add(new Shell("echo " + DEFAULT_VARIABLE_NAME + " = \"$" + DEFAULT_VARIABLE_NAME + "\""));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"This is a different message\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("This is a different message", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithChoiceParam() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "foo");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n echo \"mychoice is $mychoice\"\n}", true));
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("CI_MESSAGE", "", ""),
                new StringParameterDefinition("mychoice", "scott\ntom", "")));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("{}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        j.assertLogContains("mychoice is scott", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(
                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE))));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventSubscribeWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData()));

        jobA.getBuildersList().add(new Shell("echo $" + DEFAULT_VARIABLE_NAME));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"This is my content\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("This is my content", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(
                getSubscriberProviderData("topic", new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE))));

        jobA.getBuildersList().add(new Shell("echo $" + DEFAULT_VARIABLE_NAME));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("topic", MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_TOPIC", "topic", "")));
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData("$MY_TOPIC")));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("topic", "{\"msg\": \"Hello World\"}")));
        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_TOPIC", "topic", "")));
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(
                getSubscriberProviderData("$MY_TOPIC", new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE))));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("topic", MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithPipelineSendMsg() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "job");
        ProviderData pd = getPublisherProviderData("");
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineSendMsg() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "job");
        ProviderData pd = getPublisherProviderData(MESSAGE_CHECK_CONTENT);
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTrigger() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);
        jobA.getBuildersList().add(new Shell("echo " + DEFAULT_VARIABLE_NAME + " = $" + DEFAULT_VARIABLE_NAME));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"This is some great message.\"}")));
        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("This is some great message.", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList().add(new Shell("echo job ran"));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckNoSquash() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList().add(new Shell("sleep 3;"));
        attachTrigger(
                new CIBuildTrigger(true,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);
        j.buildAndAssertSuccess(jobB);
        j.buildAndAssertSuccess(jobB);
        j.buildAndAssertSuccess(jobB);
        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA, 5);
        assertThat(jobA.getLastBuild().getNumber(), is(equalTo(5)));

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithRegExpCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck("$.compose.compose_id", "Fedora-Atomic.+")))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo job ran"));
        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = $CI_MESSAGE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData("{ \"compose\": { \"compose_id\": \"Fedora-Atomic-25-20170105.0\" } }")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);
        jobA.getBuildersList().add(new Shell("echo " + DEFAULT_VARIABLE_NAME + " = $" + DEFAULT_VARIABLE_NAME));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"This is a message\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("This is a message", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo job ran"));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(MESSAGE_CHECK_CONTENT)));
        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    protected void waitUntilScheduledBuildCompletes() throws Exception {
        Thread.sleep(1000); // Sometimes, it needs a bit for the build to even start
        j.waitUntilNoActivityUpTo(1000 * 60);
    }

    // TODO restart tests

    @Test
    public void testSimpleCIEventTriggerWithMultipleTopics() throws Exception {
        String topic1 = testName.getMethodName() + "-1";
        String topic2 = testName.getMethodName() + "-2";
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(new CIBuildTrigger(false,
                Arrays.asList(getSubscriberProviderData(topic1, new MsgCheck("my-topic", topic1)),
                        getSubscriberProviderData(topic2, new MsgCheck("my-topic", topic2)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo $" + DEFAULT_VARIABLE_NAME));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getBuildersList()
                .add(new CIMessageBuilder(getPublisherProviderData(topic1, "{ \"my-topic\" : \"" + topic1 + "\" }")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(topic1, jobA.getLastBuild());

        FreeStyleProject jobC = j.createFreeStyleProject();
        jobC.getBuildersList()
                .add(new CIMessageBuilder(getPublisherProviderData(topic2, "{ \"my-topic\" : \"" + topic2 + "\" }")));
        j.buildAndAssertSuccess(jobC);

        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(topic2, jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
        jobC.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData("topic"))), jobA);
        jobA.getBuildersList().add(new Shell("echo " + DEFAULT_VARIABLE_NAME + " = $" + DEFAULT_VARIABLE_NAME));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_TOPIC", "topic", "")));
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("$MY_TOPIC", "{\"msg\": \"This is my message\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("This is my message", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo job ran"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("MY_TOPIC", testName.getMethodName(), "")));
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("$MY_TOPIC", MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventSubscribeWithNoParamOverride() throws Exception {
        // Job parameters are NOT overridden when the subscribe build step is used.
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("PARAMETER", "original parameter value", "")));

        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData()));

        jobA.getBuildersList().add(new Shell("echo $PARAMETER"));
        jobA.getBuildersList().add(new Shell("echo $" + DEFAULT_VARIABLE_NAME));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"This is my content\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("original parameter value", jobA.getLastBuild());
        j.assertLogContains("This is my content", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerOnPipelineJob() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n sleep 10\n}", true));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJob() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n sleep 10\n}", true));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {

        j.jenkins.getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("MY_TOPIC_ID", "topic")));

        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n sleep 10\n}", true));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData("$MY_TOPIC_ID"))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("$MY_TOPIC_ID", "Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {

        j.jenkins.getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("MY_TOPIC_ID", testName.getMethodName())));

        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n sleep 10\n}", true));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(
                getSubscriberProviderData("$MY_TOPIC_ID", new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("$MY_TOPIC_ID", MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData();
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd)));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"Hello World\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE));
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd), true));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventTriggerWithSelectorWithCheckWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE));
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd)));
        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        FreeStyleProject jobC = j.createFreeStyleProject();
        jobC.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("{\"content\": \"uncaught\"}")));
        j.buildAndAssertSuccess(jobC);

        Thread.sleep(3000); // Wait fixed ammount of time to make sure the build does NOT get scheduled
        assertThat(jobA.getLastBuild().getNumber(), is(equalTo(1)));

        jobA.delete();
        jobB.delete();
        jobC.delete();
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipeline() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData();
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd)));

        scheduleAwaitStep(jobA);

        pd = getPublisherProviderData("{\"msg\": \"abcdefg\"}");
        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "foo");
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("abcdefg", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testSimpleCIEventSendAndWaitPipelineWithVariableTopic() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData("env.MY_TOPIC");
        jobA.setDefinition(new CpsFlowDefinition(
                buildWaitForCIMessageScript(pd, "env.MY_TOPIC = \"" + testName.getMethodName() + "\""), true));

        scheduleAwaitStep(jobA);

        pd = getPublisherProviderData("env.MY_TOPIC", "{\"msg\": \"abcdefg\"}");
        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "foo");
        jobB.setDefinition(new CpsFlowDefinition(
                buildSendCIMessageScript(pd, "env.MY_TOPIC = \"" + testName.getMethodName() + "\""), true));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("abcdefg", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testJobRename() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);
        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));
        jobA.renameTo("ABC");
        waitForReceiverToBeReady(jobA.getFullName());

        jobA.delete();
    }

    @Test
    public void testJobRenameWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));
        jobA.renameTo("ABC");
        waitForReceiverToBeReady(jobA.getFullName());

        jobA.delete();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggered() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo BUILD_NUMBER = $BUILD_NUMBER"));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);
        waitForReceiverToBeReady(jobA.getFullName(), 1);
        jobA.disable();
        // Wait for trigger thread to be stopped.
        Thread.sleep(3000);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(null)));

        j.buildAndAssertSuccess(jobB);

        jobA.enable();
        // waitForReceiverToBeReady(jobA.getFullName(), 2);
        waitForReceiverToBeReady(jobA.getFullName());

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo BUILD_NUMBER = 1", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testDisabledJobDoesNotGetTriggeredWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo BUILD_NUMBER = $BUILD_NUMBER"));
        jobA.disable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(MESSAGE_CHECK_CONTENT)));
        j.buildAndAssertSuccess(jobB);

        assertThat(jobA.getBuilds(), Matchers.iterableWithSize(0));

        jobA.enable();
        waitForReceiverToBeReady(jobA.getFullName(), 2);

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo BUILD_NUMBER = 1", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testDisabledWorkflowJobDoesNotGetTriggered() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("echo \"BUILD_NUMBER = ${env.BUILD_NUMBER}\"", true));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);
        jobA.doDisable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(null)));

        j.buildAndAssertSuccess(jobB);

        assertThat(jobA.getBuilds(), Matchers.iterableWithSize(0));

        jobA.doEnable();
        waitForReceiverToBeReady(jobA.getFullName(), 2);

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("BUILD_NUMBER = 1", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testEnsureFailedSendingOfMessageFailsBuild() throws Exception {
        waitForProviderToStop(getContainerId());
        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getBuildersList().add(new CIMessageBuilder(getPublisherProviderData(null)));
        FreeStyleBuild build = j.buildAndAssertStatus(Result.FAILURE, jobB);
        j.assertLogContains("Unhandled exception in perform: ", build);

        jobB.delete();
    }

    @Test
    public void testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
        waitForProviderToStop(getContainerId());
        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");

        ProviderData pd = getPublisherProviderData("abcdefg");
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));
        WorkflowRun build = j.buildAndAssertStatus(Result.FAILURE, jobB);
        j.assertLogContains("Unhandled exception in perform: ", build);

        jobB.delete();
    }

    @Test
    public void testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData();
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd)));
        scheduleAwaitStep(jobA);
        WorkflowRun waitingBuild = jobA.getLastBuild();

        System.out.println(waitingBuild.getLog()); // Diagnose what the build is doing when it does not get interrupted
        waitingBuild.getExecutor().interrupt();

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatus(Result.ABORTED, waitingBuild);

        jobA.delete();
    }

    @Test
    public void testPipelineInvalidProvider() throws Exception {
        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");
        ProviderData pd = getPublisherProviderData("bogus", testName.getMethodName(), "");
        jobB.setDefinition(new CpsFlowDefinition(buildSendCIMessageScript(pd), true));

        WorkflowRun build = j.buildAndAssertStatus(Result.FAILURE, jobB);
        j.assertLogContains("java.lang.Exception: Unrecognized provider name: bogus", build);

        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        pd = getSubscriberProviderData("bogus", testName.getMethodName(), DEFAULT_VARIABLE_NAME, false);
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd)));
        build = j.buildAndAssertStatus(Result.FAILURE, jobA);
        j.assertLogContains("java.lang.Exception: Unrecognized provider name: bogus", build);

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testCITriggerWithFileParameter() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        String script = "if [ ! -e QWERTY ]; then\n    exit 1\nfi\ncat QWERTY; echo ''";
        List<ParameterDefinition> params = new ArrayList<>();
        for (String name : getFileNames()) {
            jobA.getBuildersList().add(new Shell(script.replaceAll("QWERTY", name)));
            params.add(new FileParameterDefinition(name, ""));
        }
        jobA.addProperty(new ParametersDefinitionProperty(params));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"Hello World\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testWaitForCIMessageStepWithFiles() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(DEFAULT_PROVIDER_NAME,
                testName.getMethodName(), DEFAULT_VARIABLE_NAME, true)));

        String script = "if [ ! -e QWERTY ]; then\n    exit 1\nfi\ncat QWERTY; echo ''";
        for (String name : getFileNames()) {
            jobA.getBuildersList().add(new Shell(script.replaceAll("QWERTY", name)));
        }
        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"Hello World\"}")));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA);

        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testWaitForCIMessagePipelineWithFiles() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData(DEFAULT_PROVIDER_NAME, testName.getMethodName(),
                DEFAULT_VARIABLE_NAME, true);
        String validate = "if (!fileExists('QWERTY')) {\n        error('File does not exist: QWERTY')\n    }\n";
        String postStatements = "";
        for (String name : getFileNames()) {
            postStatements += validate.replaceAll("QWERTY", name);
        }
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd, null, postStatements)));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"Hello World\"}")));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA);

        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testCITriggerWithMessageTooLong() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("env | sort"));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData())), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"" + Strings.repeat("a", 131061) + "\"}")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatus(Result.FAILURE, jobA.getLastBuild());
        j.assertLogContains("Argument list too long", jobA.getLastBuild());

        jobA.addProperty(new ParametersDefinitionProperty(
                Collections.singletonList(new FileParameterDefinition("CI_MESSAGE", ""))));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testWaitForCIMessageStepWithMessageTooLong() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(DEFAULT_PROVIDER_NAME,
                testName.getMethodName(), DEFAULT_VARIABLE_NAME, false)));
        jobA.getBuildersList().add(new Shell("env | sort"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"" + Strings.repeat("a", 131061) + "\"}")));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA);

        j.assertBuildStatus(Result.FAILURE, jobA.getLastBuild());
        j.assertLogContains("Argument list too long", jobA.getLastBuild());

        jobA.getBuildersList().clear();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(DEFAULT_PROVIDER_NAME,
                testName.getMethodName(), DEFAULT_VARIABLE_NAME, true)));
        jobA.getBuildersList().add(new Shell("env | sort"));
        scheduleAwaitStep(jobA, 2);

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    @Test
    public void testWaitForCIMessagePipelineWithMessageTooLong() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        ProviderData pd = getSubscriberProviderData(DEFAULT_PROVIDER_NAME, testName.getMethodName(),
                DEFAULT_VARIABLE_NAME, false);
        String postStatements = "sh 'printenv'";
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd, null, postStatements)));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData("{\"msg\": \"" + Strings.repeat("a", 131061) + "\"}")));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA);

        j.assertBuildStatus(Result.FAILURE, jobA.getLastBuild());
        j.assertLogContains("Argument list too long", jobA.getLastBuild());

        pd = getSubscriberProviderData(DEFAULT_PROVIDER_NAME, testName.getMethodName(), DEFAULT_VARIABLE_NAME, true);
        jobA.setDefinition(new CpsFlowDefinition(buildWaitForCIMessageScript(pd, null, postStatements)));
        scheduleAwaitStep(jobA, 2);

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    protected String stringFrom(Process proc) throws InterruptedException, IOException {
        int exit = proc.waitFor();
        if (exit != 0) {
            String stderr = IOUtils.toString(proc.getErrorStream(), Charset.defaultCharset());
            throw new IOException(proc.toString() + " failed with " + exit + System.lineSeparator() + stderr);
        }
        StringWriter writer = new StringWriter();
        IOUtils.copy(proc.getInputStream(), writer);
        String string = writer.toString();
        writer.close();
        return string;
    }

    protected Process logProcessBuilderIssues(ProcessBuilder pb, String commandName)
            throws InterruptedException, IOException {
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

    protected void printThreadsWithName(String tName) {
        System.out.println("Looking for Threads with name that contains: " + tName);
        List<Thread> threads = getThreadsByName(tName);

        threads.forEach(System.err::println);
    }

    protected List<Thread> getThreadsByName(String tName) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> Pattern.compile(tName).matcher(thread.getName()).matches())
                .collect(Collectors.toList());
    }

    protected int getCurrentThreadCountForName(String name) {
        return getThreadsByName(name).size();
    }
}
