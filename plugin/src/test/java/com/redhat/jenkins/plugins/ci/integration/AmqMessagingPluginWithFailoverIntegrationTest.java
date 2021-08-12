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

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.activemq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.integration.fixtures.ActiveMQContainer;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQSubscriberProviderData;
import com.redhat.utils.MessageUtils;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import hudson.util.Secret;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.test.acceptance.docker.Docker;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.StrictMath.abs;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class AmqMessagingPluginWithFailoverIntegrationTest {
    @ClassRule
    public static DockerClassRule<ActiveMQContainer> docker = new DockerClassRule<>(ActiveMQContainer.class);
    private ActiveMQContainer amq = null;

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        assumeFalse(Functions.isWindows());

        amq = docker.create();

        GlobalCIConfiguration gcc = GlobalCIConfiguration.get();
        gcc.setConfigs(Collections.singletonList(new ActiveMqMessagingProvider(
                SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME,
                createFailoverUrl(amq.getBroker()),
                true,
                "CI",
                null,
                new UsernameAuthenticationMethod("admin", Secret.fromString("redhat"))
        )));

        // TODO test connection. WebClient? Rest?
    }

    private String createFailoverUrl(String broker) {
        return "failover:(" + broker + "," + broker + ")?startupMaxReconnectAttempts=1&maxReconnectAttempts=1";
    }

    @Test
    public void testSimpleCIEventTrigger() throws Exception {
        ArrayList<FreeStyleProject> jobs = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            FreeStyleProject jobA = j.createFreeStyleProject("receiver" + i);
            jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));
            jobA.addTrigger(new CIBuildTrigger(true, Collections.singletonList(
                    new ActiveMQSubscriberProviderData(
                            SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME,
                            null,
                            "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'",
                            Collections.emptyList(),
                            "CI_MESSAGE",
                            60
                    )
            )));
        }
        waitForNoAMQTaskThreads();

        long currentThreadCount = getCurrentAMQThreadCount();
        System.out.println("Current AMQ Thread Count: " + currentThreadCount);
        String previousThreads = printAMQThreads();
        System.out.println(previousThreads);

        FreeStyleProject jobB = j.createFreeStyleProject("sender");
        jobB.getPublishersList().add(new CIMessageNotifier(new ActiveMQPublisherProviderData(
                SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME,
                null,
                MessageUtils.MESSAGE_TYPE.CodeQualityChecksDone,
                "CI_STATUS = failed",
                null,
                true
        )));

        j.buildAndAssertSuccess(jobB);

        for (FreeStyleProject job : jobs) {
            FreeStyleBuild lastBuild = job.getLastBuild();
            j.assertBuildStatusSuccess(lastBuild);
            j.assertLogContains("echo CI_TYPE = code-quality-checks-done", lastBuild);
            lastBuild.delete();
        }

        //Now stop AMQ
        System.out.println("Stopping AMQ");
        stopAMQ();
        System.out.println("Waiting 60 secs");
        Thread.sleep(60000);

        //Check for unconnection AMQ threads
        System.out.println(printAMQThreads());
        ensureNoUnconnectedThreads();

        //Now startup
        System.out.println("Starting AMQ");
        startAMQ();

        System.out.println("Waiting 10 secs");
        Thread.sleep(10000);
        waitForNoAMQTaskThreads();

        System.out.println(printAMQThreads());
        ensureNoLeakingThreads(currentThreadCount, previousThreads);

        j.buildAndAssertSuccess(jobB);

        Thread.sleep(1000);
        for (FreeStyleProject job : jobs) {
            FreeStyleBuild lastBuild = job.getLastBuild();
            j.assertBuildStatusSuccess(lastBuild);
            j.assertLogContains("echo CI_TYPE = code-quality-checks-done", lastBuild);
            lastBuild.delete();
        }
        System.out.println(printAMQThreads());
        System.out.println("Waiting 10 secs");
        Thread.sleep(10000);
        waitForNoAMQTaskThreads();
    }

    // Test setting a valid JMS selector, then fixing it, and make sure threads are handled correctly.
    @Test
    public void testInvalidJMSSelector() throws Exception {

        FreeStyleProject jobA = j.createFreeStyleProject("receiver");
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));

        jobA.addTrigger(new CIBuildTrigger(true, Collections.singletonList(
                new ActiveMQSubscriberProviderData(
                        SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME,
                        null,
                        "CI_TYPE 'code-quality-checks-done' and CI_STATUS = 'failed'",  // Missing '='; invalid syntax.
                        Collections.emptyList(),
                        "CI_MESSAGE",
                        60
                )
        )));
        jobA.getTrigger(CIBuildTrigger.class).start(jobA, true);

        Thread.sleep(5000); // Wait for connection to fail

        List<Long> ids1 = getCurrentTriggerThreadIds("receiver");
        assertEquals("Trigger threads invalid size", 0, ids1.size());

        JenkinsRule.WebClient wc = j.createWebClient();
        String source = wc.getPage(jobA).getWebResponse().getContentAsString();

        assertThat(source, containsString("CI Build Trigger Issue"));
        assertThat(source, containsString("javax.jms.InvalidSelectorException"));

        // Now fix the selector.
        jobA.addTrigger(new CIBuildTrigger(true, Collections.singletonList(
                new ActiveMQSubscriberProviderData(
                        SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME,
                        null,
                        "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'",
                        Collections.emptyList(),
                        "CI_MESSAGE",
                        60
                )
        )));
        new CIBuildTrigger(true, Collections.singletonList(
                new ActiveMQSubscriberProviderData(
                        SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME,
                        null,
                        "CI_TYPE 'code-quality-checks-done' and CI_STATUS = 'failed'",  // Missing '='; invalid syntax.
                        Collections.emptyList(),
                        "CI_MESSAGE",
                        60
                )
        )).start(jobA, true);

        List<Long> ids2 = getCurrentTriggerThreadIds("receiver");
        assertEquals("Trigger threads valid selector size", 1, ids2.size());
    }

    @Test
    public void testInvalidJMSSelectorInPipeline() throws Exception {
        // Test setting a valid JMS selector in a pipeline, then fixing it, and make sure threads are handled correctly.

        WorkflowJob pipe = j.jenkins.createProject(WorkflowJob.class, "pipeline");
        pipe.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                "    agent { label 'master' }\n" +
                "    triggers {\n" +
                "        ciBuildTrigger(noSquash: true,\n" +
                "                       providerData: activeMQSubscriber(name: '" + SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME + "',\n" +
                "                                                        overrides: [topic: \"CI\"],\n" +
                "                                                        selector: \"CI_TYPE 'code-quality-checks-done' and CI_STATUS = 'failed'\",\n" +
                "                                                       )\n" +
                "                      )\n" +
                "    }\n" +
                "    stages {\n" +
                "        stage('foo') {\n" +
                "            steps {\n" +
                "                echo 'Hello world!'\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n",
                true
        ));
        pipe.save();
        Thread.sleep(5000);

        // No trigger threads created on save. Must run once.
        List<Long> ids = getCurrentTriggerThreadIds("pipeline");
        assertEquals("Trigger threads initial pipeline save", 0, ids.size());

        j.buildAndAssertSuccess(pipe);
        Thread.sleep(5000);
        // No trigger threads created because of bad syntax.
        List<Long> ids2 = getCurrentTriggerThreadIds("pipeline");
        assertEquals("Trigger threads invalid syntax size", 0, ids2.size());

        JenkinsRule.WebClient wc = j.createWebClient();
        String source = wc.getPage(pipe).getWebResponse().getContentAsString();
        assertThat(source, containsString("CI Build Trigger Issue"));
        assertThat(source, containsString("javax.jms.InvalidSelectorException"));

        // Now fix the selector.
        pipe.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                "    agent { label 'master' }\n" +
                "    triggers {\n" +
                "        ciBuildTrigger(noSquash: true,\n" +
                "                       providerData: activeMQSubscriber(name: '" + SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME + "',\n" +
                "                                                        overrides: [topic: \"CI\"],\n" +
                "                                                        selector: \"CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'\",\n" +
                "                                                       )\n" +
                "                       )\n" +
                "    }\n" +
                "    stages {\n" +
                "        stage('foo') {\n" +
                "            steps {\n" +
                "                echo 'Hello world!'\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n",
                true
        ));
        pipe.save();
        Thread.sleep(5000);

        // Let's start a build to get new selector activated.
        j.buildAndAssertSuccess(pipe);
        Thread.sleep(5000);
        assertEquals("Number of threads", 1, getCurrentTriggerThreadIds("pipeline").size());
    }

    @Test
    public void testChangingJMSSelector() throws Exception {
        // Test changing a selector and make sure threads are handled correctly.

        FreeStyleProject jobA = j.createFreeStyleProject("receiver");
        jobA.getBuildersList().add(new Shell("echo CI_TYPE = $CI_TYPE"));

        jobA.addTrigger(new CIBuildTrigger(true, Collections.singletonList(
                new ActiveMQSubscriberProviderData(
                        SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME,
                        null,
                        "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'",
                        Collections.emptyList(),
                        "CI_MESSAGE",
                        60
                )
        )));
        jobA.getTrigger(CIBuildTrigger.class).start(jobA, true);

        List<Long> ids1 = getCurrentTriggerThreadIds("receiver");
        assertEquals("Trigger threads value selector size", 1, ids1.size());

        //Now change the selector.
        jobA.getTrigger(CIBuildTrigger.class).setProviderList(Collections.singletonList(
                new ActiveMQSubscriberProviderData(
                        SharedMessagingPluginIntegrationTest.DEFAULT_PROVIDER_NAME,
                        null,
                        "CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'success'",
                        Collections.emptyList(),
                        "CI_MESSAGE",
                        60
                )
        ));
        jobA.getTrigger(CIBuildTrigger.class).start(jobA, true);

        List<Long> ids2 = getCurrentTriggerThreadIds("receiver");
        assertEquals("Trigger threads changed selector size", 1, ids2.size());
        assertNotSame("Trigger threads new thread created", ids1.get(0), ids2.get(0));
    }

    private List<Long> getCurrentTriggerThreadIds(String name) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().matches("^CIBuildTrigger-" + Pattern.quote(name) + ".*"))
                .map(Thread::getId)
                .collect(Collectors.toList())
        ;
    }

    private long getCurrentAMQThreadCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().matches("^ActiveMQ Transport"))
                .count()
        ;
    }

    private long getCurrentAMQTaskCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().matches("^ActiveMQ.*Task-"))
                .count()
        ;
    }

    private void startAMQ() throws Exception {
        System.out.println(Docker.cmd("restart", amq.getCid()));
        System.out.println(Docker.cmd("restart", amq.getCid())
                .popen()
                .verifyOrDieWith("Unable to start container"));
        Thread.sleep(3000);
        amq.assertRunning();
    }

    private void ensureNoUnconnectedThreads() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            assertThat(thread.getName(), not(containsString("ActiveMQ Connection Executor: unconnected")));
        }
    }

    private void waitForNoAMQTaskThreads() throws InterruptedException {
        long currentThreadCount = getCurrentAMQTaskCount();
        int counter = 0;
        int MAXWAITTIME = 60;
        while (currentThreadCount != 0 && counter < MAXWAITTIME) {
            System.out.println("currentThreadCount != 0");
            System.out.println(currentThreadCount + " != " + 0);
            Thread.sleep(1000);
            counter++;
            currentThreadCount = getCurrentAMQTaskCount();
        }
        boolean equal = currentThreadCount == 0;
        assertTrue("currentThreadCount != 0", equal);
        System.out.println("currentThreadCount == 0");
    }

    private String printAMQThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.contains("ActiveMQ Transport"))
                .collect(Collectors.joining())
        ;
    }

    private void ensureNoLeakingThreads(long previousThreadCount, String previousThreads) throws InterruptedException {
        long currentThreadCount = getCurrentAMQThreadCount();
        System.out.println("Current AMQ Thread Count: " + currentThreadCount);
        int counter = 0;
        int MAXWAITTIME = 60;
        while (abs(currentThreadCount - previousThreadCount) > 2 &&
                counter < MAXWAITTIME) {
            System.out.println("abs(currentThreadCount [" + currentThreadCount
                    + "] - previousThreadCount [" + previousThreadCount + "] ) > 2");
            System.out.println(abs(currentThreadCount - previousThreadCount));
            Thread.sleep(1000);
            counter++;
            currentThreadCount = getCurrentAMQThreadCount();
        }
        boolean equal = abs(currentThreadCount - previousThreadCount) <= 2;
        if (!equal) {
            System.out.println("*** Previous Threads ***");
            System.out.println(previousThreads);
            System.out.println("************************");
            System.out.println("************************");
            System.out.println("*** Current  Threads ***");
            System.out.println(printAMQThreads());
        }
        assertTrue("abs(currentThreadCount - previousThreadCount) > 2", equal);
    }

    private void stopAMQ() throws Exception {
        System.out.println(Docker.cmd("stop", amq.getCid()));
        System.out.println(Docker.cmd("stop", amq.getCid())
                .popen()
                .verifyOrDieWith("Unable to stop container"));
        Thread.sleep(3000);
        boolean running = false;
        try {
            amq.assertRunning();
            running = false;
        } catch (Error e) {
            //This is ok
        }
        if (running) {
            throw new Exception("Container " + amq.getCid() + " not stopped");
        }
    }
}
