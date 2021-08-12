package com.redhat.jenkins.plugins.ci.integration;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.CIMessageBuilder;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.CIMessageSubscriberBuilder;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.MessageUtils;
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
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.test.acceptance.docker.Docker;
import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by shebert on 06/06/17.
 */
public abstract class SharedMessagingPluginIntegrationTest {

    @Rule public final JenkinsRule j = new JenkinsRule();

    public static String MESSAGE_CHECK_FIELD = "content";
    public static String MESSAGE_CHECK_VALUE = "catch me";
    public static String MESSAGE_CHECK_CONTENT = "{ \"" + MESSAGE_CHECK_FIELD + "\" : \"" + MESSAGE_CHECK_VALUE + "\" }";
    public static String DEFAULT_TOPIC_NAME = "topic";
    public static String DEFAULT_PROVIDER_NAME = "test";

    public abstract ProviderData getSubscriberProviderData(String topic, String variableName, String selector, MsgCheck... msgChecks);

    public abstract ProviderData getPublisherProviderData(String topic, MessageUtils.MESSAGE_TYPE type, String properties, String content);

    protected MessagingProviderOverrides overrideTopic(String topic) {
        return topic == null ? null : new MessagingProviderOverrides(topic);
    }

    public void _testVerifyModelUIPersistence() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('master') {\n echo 'hello world' \n} ", true));
        CIBuildTrigger trigger = new CIBuildTrigger(true, Collections.singletonList(getSubscriberProviderData(
                "otopic", "HELLO", null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        )));
        jobA.addTrigger(trigger);

        j.configRoundtrip(jobA);

        CIBuildTrigger ciTrigger = jobA.getTriggers().values().stream()
                .filter(t -> t instanceof CIBuildTrigger)
                .map(t -> (CIBuildTrigger) t)
                .findFirst()
                .get()
        ;

        assertThat(ciTrigger, equalTo(trigger));

        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "job");
        job.setDefinition(new CpsFlowDefinition("node('master') {\n def message = sendCIMessage " +
                " providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " overrides: [topic: 'otopic'], " +
                " failOnError: true, " +
                " messageContent: '" + MESSAGE_CHECK_CONTENT + "'}\n", true));
        j.buildAndAssertSuccess(job);

        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(jobA.getLastBuild());
    }

    public void _testSimpleCIEventSubscribe() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(
                //null, "HELLO", "CI_TYPE = 'code-quality-checks-done'"
                null, "HELLO", null
        )));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));

        jobA.scheduleBuild2(0).waitForStart();

Thread.sleep(5000);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", "Hello World"
        )));

        j.buildAndAssertSuccess(jobB);
//j.interactiveBreak();
        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithDefaultValue() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        
        jobA.getBuildersList().add(new Shell("echo hello $DEFAULTPARAM"));
        jobA.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("CI_MESSAGE", "", ""),
                new StringParameterDefinition("DEFAULTPARAM", "world", "")
        ));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(null, null, null))));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, null, "Hello World"
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("hello world", jobA.getLastBuild());

        FreeStyleBuild build = jobA.scheduleBuild2(0, new ParametersAction(new StringParameterValue("DEFAULTPARAM", "scott", ""))).get();
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("hello scott", build);
    }

    public void _testSimpleCIEventTriggerWithTextArea(String body, String matchString) throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        
        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = \"$CI_MESSAGE\""));
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(
                "CI_MESSAGE", "", ""
        )));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(null, null, null))));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, null, body
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(matchString, jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithChoiceParam(String properties, String body, String matchString) throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "foo");
        jobA.setDefinition(new CpsFlowDefinition("node('master') {\n echo \"mychoice is $mychoice\"\n}", true));
        jobA.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("CI_MESSAGE", "", ""),
                new StringParameterDefinition("mychoice", "scott\ntom", "")
        ));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(null, null, null))));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, properties, body
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        
        j.assertLogContains(matchString, jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithBoolParam(String properties, String body, String matchString) throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "foo");
        jobA.setDefinition(new CpsFlowDefinition("node('master') {\n echo \"dryrun is $dryrun, scott is $scott\"\n}", true));
        jobA.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("CI_MESSAGE", "", ""),
                new BooleanParameterDefinition("dryrun", false, ""),
                new StringParameterDefinition("scott", "", "")
        ));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(null, null, null))));

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, properties, body
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(matchString, jobA.getLastBuild());
    }

    public void _testSimpleCIEventSubscribeWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(
                null, "HELLP", null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        )));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));
        
        jobA.scheduleBuild2(0);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());

    }

    public void _testSimpleCIEventSubscribeWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(
                "otopic", "MESSAGE_CONTENT", "CI_TYPE = 'code-quality-checks-done'"
        )));

        jobA.getBuildersList().add(new Shell("echo $MESSAGE_CONTENT"));

        jobA.scheduleBuild2(0);

        FreeStyleProject jobB = j.createFreeStyleProject();

        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "otopic", MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, null, "This is my content"
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("This is my content", jobA.getLastBuild());
    }

    public void _testSimpleCIEventSubscribeWithCheckWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(
                "otopic", "MESSAGE_CONTENT", "CI_TYPE = 'code-quality-checks-done'", new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        )));

        jobA.getBuildersList().add(new Shell("echo $MESSAGE_CONTENT"));

        jobA.scheduleBuild2(0).waitForStart();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "otopic", MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());
    }

    public void _testSimpleCIEventSubscribeWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(
                "MY_TOPIC", "my-topic", ""
        )));
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(
                "$MY_TOPIC", "HELLO", "CI_TYPE = 'code-quality-checks-done'"
        )));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));
        
        jobA.scheduleBuild2(0);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "my-topic", MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", "Hello World"
        )));
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("Hello World", jobA.getLastBuild());
    }

    public void _testSimpleCIEventSubscribeWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(
                "MY_TOPIC", "my-topic", ""
        )));
        jobA.getBuildersList().add(new CIMessageSubscriberBuilder(getSubscriberProviderData(
                "$MY_TOPIC", "HELLO", null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_CONTENT)
        )));

        jobA.getBuildersList().add(new Shell("echo $HELLO"));
        
        jobA.scheduleBuild2(0);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "my-topic", null, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("catch me", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithPipelineSendMsg() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"
        ))));

        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "job");
        job.setDefinition(new CpsFlowDefinition("node('master') {\n def message = sendCIMessage " +
                " providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " messageContent: '', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}\n", true));

        j.buildAndAssertSuccess(job);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithCheckWithPipelineSendMsg() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        ))));

        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "job");
        job.setDefinition(new CpsFlowDefinition("node('master') {\n def message = sendCIMessage " +
                " providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " messageContent: '" + MESSAGE_CHECK_CONTENT + "'}\n", true));

        j.buildAndAssertSuccess(job);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
    }

    public void _testSimpleCIEventTrigger() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(null, null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"))));
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", null
        )));
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_TYPE = code-quality-checks-done", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        
        jobA.getBuildersList().add(new Shell("echo job ran"));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        ))));

        FreeStyleProject jobB = j.createFreeStyleProject();
        
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithCheckNoSquash() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        
        jobA.getBuildersList().add(new Shell("sleep 3;"));
        jobA.addTrigger(new CIBuildTrigger(true, Collections.singletonList(getSubscriberProviderData(
                null, null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        ))));

        FreeStyleProject jobB = j.createFreeStyleProject();
        
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);
        j.buildAndAssertSuccess(jobB);
        j.buildAndAssertSuccess(jobB);
        j.buildAndAssertSuccess(jobB);
        j.buildAndAssertSuccess(jobB);

        assertThat(jobA.getLastBuild().getNumber(), is(equalTo(5)));
    }

    public void _testSimpleCIEventTriggerWithWildcardInSelector() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, "compose LIKE '%compose_id\": \"Fedora-Atomic%'")
        )));
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed\n compose = \"compose_id\": \"Fedora-Atomic-25-20170105.0\"", ""
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_TYPE = code-quality-checks-done", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithRegExpCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, null, new MsgCheck("$.compose.compose_id", "Fedora-Atomic.+")
        ))));
        jobA.getBuildersList().add(new Shell("echo job ran"));
        jobA.getBuildersList().add(new Shell("echo CI_MESSAGE = $CI_MESSAGE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, null, "{ \"compose\": { \"compose_id\": \"Fedora-Atomic-25-20170105.0\" } }"
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                "otopic", null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"
        ))));
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "otopic", MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", null
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_TYPE = code-quality-checks-done", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithCheckWithTopicOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                "otopic", null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        ))));
        jobA.getBuildersList().add(new Shell("echo job ran"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "otopic", null, null, MESSAGE_CHECK_CONTENT
        )));

        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());
    }

    // TODO restart tests

    public void _testSimpleCIEventTriggerWithMultipleTopics() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Arrays.asList(
                getSubscriberProviderData("topic1", null, null, new MsgCheck("my-topic", "topic1")),
                getSubscriberProviderData("topic2", null, null, new MsgCheck("my-topic", "topic2"))
        )));
        jobA.getBuildersList().add(new Shell("echo $CI_MESSAGE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getBuildersList().add(new CIMessageBuilder(getPublisherProviderData(
                "topic1", MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, null, "{ \"my-topic\" : \"topic1\" }"
        )));

        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("topic1", jobA.getLastBuild());

        FreeStyleProject jobC = j.createFreeStyleProject();
        jobC.getBuildersList().add(new CIMessageBuilder(getPublisherProviderData(
                "topic2", MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, null, "{ \"my-topic\" : \"topic2\" }"
        )));
        j.buildAndAssertSuccess(jobC);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("topic2", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                "org.fedoraproject.my-topic", null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"
        ))));
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(
                "MY_TOPIC", "org.fedoraproject.my-topic", ""
        )));
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "$MY_TOPIC", MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", null
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_TYPE = code-quality-checks-done", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithCheckWithTopicOverrideAndVariableTopic() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                "org.fedoraproject.my-topic", null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        ))));
        jobA.getBuildersList().add(new Shell("echo job ran"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(
                "MY_TOPIC", "org.fedoraproject.my-topic", ""
        )));
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "$MY_TOPIC", null, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithParamOverride() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, "CI_TYPE = 'code-quality-checks-done'"
        ))));

        jobA.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("PARAMETER", "bad parameter value", ""),
                new StringParameterDefinition("status", "unknown status", "")
        ));
        jobA.getBuildersList().add(new Shell("echo $PARAMETER"));
        jobA.getBuildersList().add(new Shell("echo $CI_MESSAGE"));
        jobA.getBuildersList().add(new Shell("echo status::$status"));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "PARAMETER = my parameter\nstatus=${BUILD_STATUS}\nCOMPOUND = Z${PARAMETER}Z", "This is my content with ${COMPOUND} ${BUILD_STATUS}"
        )));

        j.buildAndAssertSuccess(jobB);

        FreeStyleBuild lastBuild = jobA.getLastBuild();
        j.assertBuildStatusSuccess(lastBuild);
        j.assertLogContains("status::SUCCESS", lastBuild);
        j.assertLogContains("my parameter", lastBuild);
        j.assertLogContains("This is my content with Zmy parameterZ SUCCESS", lastBuild);
    }

    public void _testSimpleCIEventTriggerHeadersInEnv(FreeStyleProject jobB, String expected) throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, "CI_TYPE = 'code-quality-checks-done'"
        ))));

        // We are only checking that this shows up in the console output.
        jobA.getBuildersList().add(new Shell("echo $MESSAGE_HEADERS"));
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, null, "some irrelevant content"
        )));

        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains(expected, jobA.getLastBuild());
    }

    public void _testSimpleCIEventSubscribeWithNoParamOverride() throws Exception {
        // Job parameters are NOT overridden when the subscribe build step is used.
        FreeStyleProject jobA = j.createFreeStyleProject();

        jobA.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(
                "PARAMETER", "original parameter value", ""
        )));

        jobA.getBuildersList().add(new CIMessageBuilder(getSubscriberProviderData(
                null, "MESSAGE_CONTENT", "CI_TYPE = 'code-quality-checks-done'"
        )));

        jobA.getBuildersList().add(new Shell("echo $PARAMETER"));
        jobA.getBuildersList().add(new Shell("echo $MESSAGE_CONTENT"));

        jobA.scheduleBuild2(0);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "PARAMETER = my parameter", "This is my content"
        )));

        j.buildAndAssertSuccess(jobB);

        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("original parameter value", jobA.getLastBuild());
        j.assertLogContains("This is my content", jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerOnPipelineJob() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('master') {\n sleep 10\n}", true));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"
        ))));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", "Hello World"
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithCheckOnPipelineJob() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('master') {\n sleep 10\n}", true));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        ))));

        FreeStyleProject jobB = j.createFreeStyleProject();
        
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {

        j.jenkins.getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("MY_TOPIC_ID", "MY_UUID")
        ));

        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('master') {\n sleep 10\n}", true));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                "$MY_TOPIC_ID", null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"
        ))));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "$MY_TOPIC_ID", MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", "Hello World"
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithCheckOnPipelineJobWithGlobalEnvVarInTopic() throws Exception {

        j.jenkins.getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("MY_TOPIC_ID", "MY_UUID")
        ));

        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("node('master') {\n sleep 10\n}", true));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                "$MY_TOPIC_ID", null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        ))));

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                "$MY_TOPIC_ID", null, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithPipelineWaitForMsg() throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n def scott = waitForCIMessage  providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"  \necho \"scott = \" + scott}", true));
        
        wait.scheduleBuild2(0);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", "Hello World"
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(wait.getLastBuild());
        j.assertLogContains("Hello World", wait.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithCheckWithPipelineWaitForMsg() throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n def scott = waitForCIMessage  providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " checks: [[field: '" + MESSAGE_CHECK_FIELD + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']]\n" +
                "}", true));
        
        wait.scheduleBuild2(0);

        FreeStyleProject jobB = j.createFreeStyleProject();
        
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);

        j.assertBuildStatusSuccess(wait.getLastBuild());
        j.assertLogContains("Hello World", wait.getLastBuild());
    }

    public void _testSimpleCIEventTriggerWithSelectorWithCheckWithPipelineWaitForMsg() throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n def scott = waitForCIMessage  providerName: '" + DEFAULT_PROVIDER_NAME + "'," +
                " selector: \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"," +
                " checks: [[field: '" + MESSAGE_CHECK_FIELD + "', expectedValue: '" + MESSAGE_CHECK_VALUE + "']]\n" +
                "}", true));
        wait.scheduleBuild2(0);

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, null, MESSAGE_CHECK_CONTENT
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(wait.getLastBuild());
        j.assertLogContains("catch me", wait.getLastBuild());

        FreeStyleProject jobC = j.createFreeStyleProject();
        jobC.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", "{\"content\": \"uncaught\"}"
        )));
        j.buildAndAssertSuccess(jobC);

        Thread.sleep(3000);
        assertThat(wait.getLastBuild().getNumber(), is(equalTo(1)));
    }

    public void _testSimpleCIEventSendAndWaitPipeline(WorkflowJob send, String expected) throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n def scott = waitForCIMessage providerName: '" + DEFAULT_PROVIDER_NAME + "'," +
                "selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\",  " +
                " overrides: [topic: 'org.fedoraproject.otopic']" +
                "\necho \"scott = \" + scott}", true));
        
        wait.scheduleBuild2(0);

        
        send.setDefinition(new CpsFlowDefinition("node('master') {\n sendCIMessage" +
                " providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " topic: 'org.fedoraproject.otopic'," +
                " messageContent: 'abcdefg', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}", true));

        j.buildAndAssertSuccess(send);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(wait.getLastBuild());
        j.assertLogContains(expected, wait.getLastBuild());
    }

    public void _testSimpleCIEventSendAndWaitPipelineWithVariableTopic(WorkflowJob send, String selector,
                                                                       String expected) throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n" +
                "    env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                "    def scott = waitForCIMessage providerName: '" + DEFAULT_PROVIDER_NAME + "', selector:  \"" +
                selector + "${env.MY_TOPIC}'\",        overrides: [topic: \"${env.MY_TOPIC}\"]\n" +
                "    echo \"scott = \" + scott\n" +
                "}", true));
        
        wait.scheduleBuild2(0);

        
        send.setDefinition(new CpsFlowDefinition("node('master') {\n" +
                " env.MY_TOPIC = 'org.fedoraproject.my-topic'\n" +
                " sendCIMessage providerName: '" + DEFAULT_PROVIDER_NAME + "', overrides: [topic: \"${env.MY_TOPIC}\"], messageContent: 'abcdefg', messageProperties: 'CI_STATUS = failed', messageType: 'CodeQualityChecksDone'\n" +
                "}", true));

        j.buildAndAssertSuccess(send);

        Thread.sleep(1000);
        j.assertBuildStatusSuccess(wait.getLastBuild());
        j.assertLogContains(expected, wait.getLastBuild());
    }

    public boolean isSubscribed(String job) {
        return true;
        // TODO reimplement
//        try {
//            JenkinsLogger logger = jenkins.getLogger("all");
//            logger.waitForLogged(Pattern.compile("Successfully subscribed job \'" +
//                    job + "\' to.*"));
//            return true;
//        } catch (Exception ex) {
//            return false;
//        }
    }

    public void _testJobRename() throws IOException, InterruptedException {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"
        ))));
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));

        Thread.sleep(1000);

        jobA.renameTo("ABC");
        Thread.sleep(3000);
        assertThat("Trigger not subscribed", isSubscribed("ABC"));
    }

    public void _testJobRenameWithCheck() throws IOException, InterruptedException {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        ))));
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));
        
        Thread.sleep(1000);

        jobA.renameTo("ABC");
        Thread.sleep(3000);
        assertThat("Trigger not subscribed", isSubscribed("ABC"));
    }

    public void _testDisabledJobDoesNotGetTriggered() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"
        ))));
        jobA.disable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", null
        )));
        
        j.buildAndAssertSuccess(jobB);

        jobA.enable();

        j.buildAndAssertSuccess(jobB);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo CI_TYPE = code-quality-checks-done", jobA.getLastBuild());
    }

    public void _testDisabledJobDoesNotGetTriggeredWithCheck() throws Exception {
        FreeStyleProject jobA = j.createFreeStyleProject();
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, null, new MsgCheck(MESSAGE_CHECK_FIELD, MESSAGE_CHECK_VALUE)
        ))));
        jobA.getBuildersList().add(new Shell("echo job ran"));
        jobA.disable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, null, null, MESSAGE_CHECK_CONTENT
        )));
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(5000);
        assertThat(jobA.getBuilds(), Matchers.iterableWithSize(0));

        jobA.enable();
        
        Thread.sleep(5000);
        j.buildAndAssertSuccess(jobB);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("echo job ran", jobA.getLastBuild());
    }

    public void _testDisabledWorkflowJobDoesNotGetTriggered() throws Exception {
        WorkflowJob jobA = j.jenkins.createProject(WorkflowJob.class, "jobA");
        jobA.setDefinition(new CpsFlowDefinition("echo \"CI_TYPE = ${env.CI_TYPE}\"", true));
        jobA.addTrigger(new CIBuildTrigger(false, Collections.singletonList(getSubscriberProviderData(
                null, null, "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'"
        ))));
        jobA.doDisable();

        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getPublishersList().add(new CIMessageNotifier(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", null
        )));
        
        j.buildAndAssertSuccess(jobB);

        Thread.sleep(5000);
        assertThat(jobA.getBuilds(), Matchers.iterableWithSize(0));
        
        jobA.doEnable();
        
        Thread.sleep(5000);
        j.buildAndAssertSuccess(jobB);
        j.assertBuildStatusSuccess(jobA.getLastBuild());
        j.assertLogContains("CI_TYPE = code-quality-checks-done", jobA.getLastBuild());
    }

    public void _testEnsureFailedSendingOfMessageFailsBuild() throws Exception {
        FreeStyleProject jobB = j.createFreeStyleProject();
        jobB.getBuildersList().add(new CIMessageBuilder(getPublisherProviderData(
                null, MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone, "CI_STATUS = failed", null
        )));
        j.buildAndAssertStatus(Result.FAILURE, jobB);
        j.assertLogContains("Unhandled exception in perform: ", jobB.getLastBuild());
    }

    public void _testEnsureFailedSendingOfMessageFailsPipelineBuild() throws Exception {
        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "send");
        
        send.setDefinition(new CpsFlowDefinition("node('master') {\n sendCIMessage" +
                " providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " failOnError: true, " +
                " messageContent: 'abcdefg', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}", true));
        j.buildAndAssertStatus(Result.FAILURE, send);
        j.assertLogContains("Unhandled exception in perform: ", send.getLastBuild());
    }

    public void _testAbortWaitingForMessageWithPipelineBuild() throws Exception {
        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n def scott = waitForCIMessage  providerName: '" + DEFAULT_PROVIDER_NAME + "', " +
                " selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"  \n}", true));

        WorkflowRun waitingBuild = wait.scheduleBuild2(0).get();
        Thread.sleep(3000);

        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(waitingBuild.getUrl() + "stop");
        HttpResponse response = httpclient.execute(post);
        if (response.getStatusLine().getStatusCode() >= 400) {
            throw new IOException("Failed to stop build: " + response.getStatusLine() + "\n" +
                    IOUtils.toString(response.getEntity().getContent()));
        } else {
            System.out.println("Build stopped! (status code: " + response.getStatusLine().getStatusCode() + ")");
        }

        j.assertBuildStatus(Result.ABORTED, waitingBuild);
    }

    public void _testPipelineInvalidProvider() throws Exception {
        WorkflowJob send = j.jenkins.createProject(WorkflowJob.class, "send");
        send.setDefinition(new CpsFlowDefinition("node('master') {\n def message = sendCIMessage " +
                " providerName: 'bogus', " +
                " messageContent: '', " +
                " messageProperties: 'CI_STATUS = failed'," +
                " messageType: 'CodeQualityChecksDone'}\n", true));

        j.buildAndAssertStatus(Result.FAILURE, send);
        j.assertLogContains("java.lang.Exception: Unrecognized provider name.", send.getLastBuild());

        WorkflowJob wait = j.jenkins.createProject(WorkflowJob.class, "wait");
        wait.setDefinition(new CpsFlowDefinition("node('master') {\n def scott = waitForCIMessage  providerName: 'bogus', " +
                " selector: " +
                " \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\"  \necho \"scott = \" + scott}", true));
        j.buildAndAssertStatus(Result.FAILURE, wait);
        j.assertLogContains("java.lang.Exception: Unrecognized provider name.", wait.getLastBuild());
    }

    protected String stringFrom(Process proc) throws InterruptedException, IOException {
        assertThat(proc.waitFor(), is(equalTo(0)));
        StringWriter writer = new StringWriter();
        IOUtils.copy(proc.getInputStream(), writer);
        String string = writer.toString();
        writer.close();
        return string;
    }

    protected Process logProcessBuilderIssues(ProcessBuilder pb, String commandName) throws InterruptedException, IOException {
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

    protected void stopContainer(DockerContainer container) throws Exception {
        System.out.println(Docker.cmd("stop", container.getCid())
                .popen()
                .verifyOrDieWith("Unable to stop container"));
        Thread.sleep(3000);
        try {
            container.assertRunning();
        } catch (Error e) {
            //This is ok
        }
    }

    protected void printThreadsWithName(String tName) {
        System.out.println("Looking for Threads with name that contains: " + tName);
        List<String> threads = getThreadsByName(tName);

        threads.forEach(System.err::println);
    }

    private List<String> getThreadsByName(String tName) {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> Pattern.compile(tName).matcher(name).matches())
                .collect(Collectors.toList())
        ;
    }

    protected int getCurrentThreadCountForName(String name) {
        return getThreadsByName(name).size();
    }
}

