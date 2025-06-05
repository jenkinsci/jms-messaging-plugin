package com.redhat.jenkins.plugins.ci.integration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.CIMessageBuilder;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.CIMessageSubscriberBuilder;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

import hudson.model.BooleanParameterDefinition;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
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
    public static String DEFAULT_TOPIC_NAME = "topic";
    public static String DEFAULT_PROVIDER_NAME = "test";

    public abstract ProviderData getSubscriberProviderData(String topic, String variableName, String selector,
            MsgCheck... msgChecks);

    public abstract ProviderData getPublisherProviderData(String topic, String properties, String content);

    public void _testVerifyModelUIPersistence() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n echo 'hello world' \n} ", true));
        CIBuildTrigger trigger = new CIBuildTrigger(true,
                Collections.singletonList(getSubscriberProviderData(testName.getMethodName(), "HELLO", null,
                        new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE))));
        attachTrigger(trigger, jobA);

        j.configRoundtrip(jobA);

        CIBuildTrigger ciTrigger = jobA.getTriggers().values().stream().filter(t -> t instanceof CIBuildTrigger)
                .map(t -> (CIBuildTrigger) t).findFirst().get();

        assertThat(ciTrigger, equalTo(trigger));

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "jobB");
        jobB.setDefinition(
                new CpsFlowDefinition(
                        "node('built-in') {\n def message = sendCIMessage " + " providerName: '" + DEFAULT_PROVIDER_NAME
                                + "', " + " overrides: [topic: '" + testName.getMethodName() + "'], "
                                + " failOnError: true, " + " messageContent: '" + MESSAGE_CHECK_CONTENT + "'}\n",
                        true));
        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventSubscribe() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(
                new CIMessageSubscriberBuilder(getSubscriberProviderData(testName.getMethodName(), "HELLO", null)));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", "Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithDefaultValue() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList().add(new Shell("echo hello $DEFAULTPARAM"));
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("CI_MESSAGE", "", ""),
                new StringParameterDefinition("DEFAULTPARAM", "world", "")));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(getSubscriberProviderData(testName.getMethodName(), null, null))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, "Hello World")));

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

    public void _testSimpleCIEventTriggerWithTextArea(String body, String matchString) throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = \"$CI_MESSAGE\""));
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("CI_MESSAGE", "", "")));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(getSubscriberProviderData(testName.getMethodName(), null, null))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, body)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(matchString, jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithChoiceParam(String properties, String body, String matchString)
            throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "foo");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n echo \"mychoice is $mychoice\"\n}", true));
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("CI_MESSAGE", "", ""),
                new StringParameterDefinition("mychoice", "scott\ntom", "")));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(getSubscriberProviderData(testName.getMethodName(), null, null))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), properties, body)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        j.assertLogContains(matchString, jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithBoolParam(String properties, String body, String matchString)
            throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "foo");
        jobA.setDefinition(
                new CpsFlowDefinition("node('built-in') {\n echo \"dryrun is $dryrun, scott is $scott\"\n}", true));
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("CI_MESSAGE", "", ""),
                new BooleanParameterDefinition("dryrun", false, ""), new StringParameterDefinition("scott", "", "")));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(getSubscriberProviderData(testName.getMethodName(), null, null))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), properties, body)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(matchString, jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventSubscribeWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(testName.getMethodName(),
                "HELLP", "", new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE))));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventSubscribeWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(
                getSubscriberProviderData(testName.getMethodName(), "MESSAGE_CONTENT", "CI_STATUS = 'failed'")));

        jobA.getBuildersList().add(new Shell("echo $MESSAGE_CONTENT"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", "This is my content")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("This is my content", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventSubscribeWithCheckWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(testName.getMethodName(),
                "MESSAGE_CONTENT", "CI_STATUS = 'failed'", new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE))));

        jobA.getBuildersList().add(new Shell("echo $MESSAGE_CONTENT"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("MY_TOPIC", testName.getMethodName(), "")));
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(
                getSubscriberProviderData("$MY_TOPIC", "HELLO", "CI_STATUS = 'failed'")));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", "Hello World")));
        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("MY_TOPIC", testName.getMethodName(), "")));
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData("$MY_TOPIC", "HELLO", null,
                new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE))));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithPipelineSendMsg() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'failed'"))),
                jobA);

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "job");
        jobB.setDefinition(
                new CpsFlowDefinition("node('built-in') {\n def message = sendCIMessage " + " providerName: '"
                        + DEFAULT_PROVIDER_NAME + "', " + " overrides: [topic: '" + testName.getMethodName() + "'], "
                        + " messageContent: '', " + " messageProperties: 'CI_STATUS = failed'}\n", true));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithCheckWithPipelineSendMsg() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "job");
        jobB.setDefinition(new CpsFlowDefinition("node('built-in') {\n def message = sendCIMessage "
                + " providerName: '" + DEFAULT_PROVIDER_NAME + "', " + " overrides: [topic: '"
                + testName.getMethodName() + "'], " + " messageContent: '" + MESSAGE_CHECK_CONTENT + "'}\n", true));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTrigger() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'failed'"))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = $CI_MESSAGE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(),
                "CI_STATUS = failed", "This is some great message.")));
        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_MESSAGE = This is some great message.", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList().add(new Shell("echo job ran"));
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithCheckNoSquash() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.getBuildersList().add(new Shell("sleep 3;"));
        attachTrigger(
                new CIBuildTrigger(true, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, MESSAGE_CHECK_CONTENT)));

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

    public void _testSimpleCIEventTriggerWithWildcardInSelector() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, "compose LIKE '%compose_id\": \"Fedora-Atomic%'"))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(),
                "CI_STATUS = failed\n compose = \"compose_id\": \"Fedora-Atomic-25-20170105.0\"", "")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_STATUS = failed", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithRegExpCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck("$.compose.compose_id", "Fedora-Atomic.+")))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo job ran"));
        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = $CI_MESSAGE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null,
                "{ \"compose\": { \"compose_id\": \"Fedora-Atomic-25-20170105.0\" } }")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'failed'"))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = $CI_MESSAGE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", "This is a message")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_MESSAGE = This is a message", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithCheckWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo job ran"));
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, MESSAGE_CHECK_CONTENT)));
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

    public void _testSimpleCIEventTriggerWithMultipleTopics() throws Exception {
        String topic1 = testName.getMethodName() + "-1";
        String topic2 = testName.getMethodName() + "-2";
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Arrays.asList(getSubscriberProviderData(topic1, null, null, new MsgCheck("my-topic", topic1)),
                                getSubscriberProviderData(topic2, null, null, new MsgCheck("my-topic", topic2)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo $CI_MESSAGE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getBuildersList().add(
                new CIMessageBuilder(getPublisherProviderData(topic1, null, "{ \"my-topic\" : \"" + topic1 + "\" }")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(topic1, jobA.getLastBuild());

        FreeStyleProject jobC = j.createFreeStyleProject();
        jobC.getBuildersList().add(
                new CIMessageBuilder(getPublisherProviderData(topic2, null, "{ \"my-topic\" : \"" + topic2 + "\" }")));
        j.buildAndAssertSuccess(jobC);

        waitUntilTriggeredBuildCompletes(jobA, 2);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(topic2, jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
        jobC.delete();
    }

    public void _testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'failed'"))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = $CI_MESSAGE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("MY_TOPIC", testName.getMethodName(), "")));
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData("$MY_TOPIC", "CI_STATUS = failed", "this is my message")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_MESSAGE = this is my message", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo job ran"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("MY_TOPIC", testName.getMethodName(), "")));
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("$MY_TOPIC", null, MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithParamOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'failed'"))),
                jobA);

        jobA.addProperty(
                new ParametersDefinitionProperty(new StringParameterDefinition("PARAMETER", "bad parameter value", ""),
                        new StringParameterDefinition("status", "unknown status", "")));
        jobA.getBuildersList().add(new Shell("echo $PARAMETER"));
        jobA.getBuildersList().add(new Shell("echo $CI_MESSAGE"));
        jobA.getBuildersList().add(new Shell("echo status::$status"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(),
                "CI_STATUS = failed\nPARAMETER = my parameter\nstatus=${BUILD_STATUS}\nCOMPOUND = Z${PARAMETER}Z",
                "This is my content with ${COMPOUND} ${BUILD_STATUS}")));

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

    public void _testSimpleCIEventTriggerHeadersInEnv(FreeStyleProject jobB, String variable, String expected)
            throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'passed'"))),
                jobA);

        // We are only checking that this shows up in the console output.
        jobA.getBuildersList().add(new Shell("echo $" + variable));
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "CI_STATUS = passed", "some irrelevant content")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(expected, jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventSubscribeWithNoParamOverride() throws Exception {
        // Job parameters are NOT overridden when the subscribe build step is used.
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("PARAMETER", "original parameter value", "")));

        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(
                getSubscriberProviderData(testName.getMethodName(), "MESSAGE_CONTENT", "PARAMETER = 'my parameter'")));

        jobA.getBuildersList().add(new Shell("echo $PARAMETER"));
        jobA.getBuildersList().add(new Shell("echo $MESSAGE_CONTENT"));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "PARAMETER = my parameter", "This is my content")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("original parameter value", jobA.getLastBuild());
        j.assertLogContains("This is my content", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerOnPipelineJob() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n sleep 10\n}", true));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'failed'"))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", "Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithCheckOnPipelineJob() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n sleep 10\n}", true));
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {

        j.jenkins.getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("MY_TOPIC_ID", testName.getMethodName())));

        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n sleep 10\n}", true));
        attachTrigger(new CIBuildTrigger(false,
                Collections.singletonList(getSubscriberProviderData("$MY_TOPIC_ID", null, "CI_STATUS = 'failed'"))),
                jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData("$MY_TOPIC_ID", "CI_STATUS = failed", "Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {

        j.jenkins.getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("MY_TOPIC_ID", testName.getMethodName())));

        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n sleep 10\n}", true));
        attachTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData("$MY_TOPIC_ID",
                null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))), jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList()
                .add(new CIMessageNotifier(getPublisherProviderData("$MY_TOPIC_ID", null, MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);
        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n def scott = waitForCIMessage  providerName: '"
                + DEFAULT_PROVIDER_NAME + "', " + " overrides: [topic: '" + testName.getMethodName() + "'], "
                + " selector: " + " \"CI_STATUS = 'failed'\"  \necho \"scott = \" + scott}", true));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", "Hello World")));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());

        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        jobA.setDefinition(new CpsFlowDefinition(
                "node('built-in') {\n def scott = waitForCIMessage  providerName: '" + DEFAULT_PROVIDER_NAME + "', "
                        + " overrides: [topic: '" + testName.getMethodName() + "'], " + " checks: [[field: '"
                        + MESSAGE_CHECK_FIELD + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']]\n" + "}",
                true));

        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventTriggerWithSelectorWithCheckWithPipelineWaitForMsg() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n def scott = waitForCIMessage  providerName: '"
                + DEFAULT_PROVIDER_NAME + "'," + " overrides: [topic: '" + testName.getMethodName() + "'], "
                + " selector: \"CI_STATUS = 'failed'\"," + " checks: [[field: '" + MESSAGE_CHECK_FIELD
                + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']]\n" + "}", true));
        scheduleAwaitStep(jobA);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(
                getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", MESSAGE_CHECK_CONTENT)));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

        FreeStyleProject jobC = j.createFreeStyleProject();
        jobC.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(),
                "CI_STATUS = failed", "{\"content\": \"uncaught\"}")));
        j.buildAndAssertSuccess(jobC);

        Thread.sleep(3000); // Wait fixed ammount of time to make sure the build does NOT get scheduled
        assertThat(jobA.getLastBuild().getNumber(), is(equalTo(1)));

        jobA.delete();
        jobB.delete();
        jobC.delete();
    }

    public void _testSimpleCIEventSendAndWaitPipeline(WorkflowJob jobB, String expected) throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n def scott = waitForCIMessage providerName: '"
                + DEFAULT_PROVIDER_NAME + "'," + "selector: " + " \"CI_STATUS = 'failed'\",  "
                + " overrides: [topic: 'org.fedoraproject.otopic']" + "\necho \"scott = \" + scott}", true));

        scheduleAwaitStep(jobA);

        jobB.setDefinition(new CpsFlowDefinition("node('built-in') {\n sendCIMessage" + " providerName: '"
                + DEFAULT_PROVIDER_NAME + "', " + " overrides: [topic: 'org.fedoraproject.otopic'],"
                + " messageContent: 'abcdefg', " + " messageProperties: 'CI_STATUS = failed'}", true));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(expected, jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testSimpleCIEventSendAndWaitPipelineWithVariableTopic(WorkflowJob jobB, String selector,
            String expected) throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n" + "    env.MY_TOPIC = '"
                + testName.getMethodName() + "'\n" + "    def scott = waitForCIMessage providerName: '"
                + DEFAULT_PROVIDER_NAME + "', selector:  \"" + selector
                + "${env.MY_TOPIC}'\",        overrides: [topic: \"${env.MY_TOPIC}\"]\n"
                + "    echo \"scott = \" + scott\n" + "}", true));

        scheduleAwaitStep(jobA);

        jobB.setDefinition(new CpsFlowDefinition("node('built-in') {\n" + " env.MY_TOPIC = '" + testName.getMethodName()
                + "'\n" + " sendCIMessage providerName: '" + DEFAULT_PROVIDER_NAME
                + "', overrides: [topic: \"${env.MY_TOPIC}\"], messageContent: 'abcdefg', messageProperties: 'CI_STATUS = failed'\n"
                + "}", true));

        j.buildAndAssertSuccess(jobB);

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(expected, jobA.getLastBuild());

        jobA.delete();
        jobB.delete();
    }

    public void _testJobRename() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'failed'"))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));
        jobA.renameTo("ABC");
        waitForReceiverToBeReady(jobA.getFullName());

        jobA.delete();
    }

    public void _testJobRenameWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo CI_STATUS = ${CI_STATUS}"));
        jobA.renameTo("ABC");
        waitForReceiverToBeReady(jobA.getFullName());

        jobA.delete();
    }

    public void _testDisabledJobDoesNotGetTriggered() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new Shell("echo BUILD_NUMBER = $BUILD_NUMBER"));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'failed'"))),
                jobA);
        waitForReceiverToBeReady(jobA.getFullName(), 1);
        jobA.disable();
        // Wait for trigger thread to be stopped.
        Thread.sleep(3000);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", null)));

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

    public void _testDisabledJobDoesNotGetTriggeredWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        attachTrigger(
                new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(testName.getMethodName(),
                        null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)))),
                jobA);
        jobA.getBuildersList().add(new Shell("echo BUILD_NUMBER = $BUILD_NUMBER"));
        jobA.disable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), null, MESSAGE_CHECK_CONTENT)));
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

    public void _testDisabledWorkflowJobDoesNotGetTriggered() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("echo \"BUILD_NUMBER = ${env.BUILD_NUMBER}\"", true));
        attachTrigger(
                new CIBuildTrigger(false,
                        Collections.singletonList(
                                getSubscriberProviderData(testName.getMethodName(), null, "CI_STATUS = 'failed'"))),
                jobA);
        jobA.doDisable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(
                new CIMessageNotifier(getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", null)));

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

    public void _testEnsureFailedSendingOfMessageFailsBuild() throws Exception {
        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getBuildersList().add(
                new CIMessageBuilder(getPublisherProviderData(testName.getMethodName(), "CI_STATUS = failed", null)));
        FreeStyleBuild build = j.buildAndAssertStatus(Result.FAILURE, jobB);
        j.assertLogContains("Unhandled exception in perform: ", build);

        jobB.delete();
    }

    public void _testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");

        jobB.setDefinition(new CpsFlowDefinition("node('built-in') {\n sendCIMessage" + " providerName: '"
                + DEFAULT_PROVIDER_NAME + "', " + " failOnError: true, " + " messageContent: 'abcdefg', "
                + " messageProperties: 'CI_STATUS = failed'}", true));
        WorkflowRun build = j.buildAndAssertStatus(Result.FAILURE, jobB);
        j.assertLogContains("Unhandled exception in perform: ", build);

        jobB.delete();
    }

    public void _testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        jobA.setDefinition(new CpsFlowDefinition("node('built-in') {\n def scott = waitForCIMessage  providerName: '"
                + DEFAULT_PROVIDER_NAME + "', " + " selector: " + " \"CI_STATUS = 'failed'\"  \n}", true));
        scheduleAwaitStep(jobA);
        WorkflowRun waitingBuild = jobA.getLastBuild();

        System.out.println(waitingBuild.getLog()); // Diagnose what the build is doing when it does not get interrupted
        waitingBuild.getExecutor().interrupt();

        waitUntilTriggeredBuildCompletes(jobA);
        j.assertBuildStatus(Result.ABORTED, waitingBuild);

        jobA.delete();
    }

    public void _testPipelineInvalidProvider() throws Exception {
        WorkflowJob jobB = j.jenkins.createProject(WorkflowJob.class, "send");
        jobB.setDefinition(
                new CpsFlowDefinition("node('built-in') {\n def message = sendCIMessage " + " providerName: 'bogus', "
                        + " messageContent: '', " + " messageProperties: 'CI_STATUS = failed'}\n", true));

        WorkflowRun build = j.buildAndAssertStatus(Result.FAILURE, jobB);
        j.assertLogContains("java.lang.Exception: Unrecognized provider name: bogus", build);

        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "wait");
        jobA.setDefinition(
                new CpsFlowDefinition("node('built-in') {\n def scott = waitForCIMessage  providerName: 'bogus', "
                        + " selector: " + " \"CI_STATUS = 'failed'\"  \necho \"scott = \" + scott}", true));
        build = j.buildAndAssertStatus(Result.FAILURE, jobA);
        j.assertLogContains("java.lang.Exception: Unrecognized provider name: bogus", build);

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
