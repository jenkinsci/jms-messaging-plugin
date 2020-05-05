package com.redhat.jenkins.plugins.ci.integration;

import static java.lang.StrictMath.abs;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.ActiveMQContainer;
import org.jenkinsci.test.acceptance.docker.Docker;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Plugin;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.redhat.jenkins.plugins.ci.integration.po.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger.ProviderData;
import com.redhat.jenkins.plugins.ci.integration.po.CINotifierPostBuildStep;
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
@WithPlugins({"jms-messaging", "dumpling", "monitoring"})
@WithDocker
public class AmqMessagingPluginWithFailoverIntegrationTest extends AbstractJUnitTest {
    @Inject private DockerContainerHolder<ActiveMQContainer> docker1;

    private ActiveMQContainer amq1 = null;
    private ActiveMqMessagingProvider msgConfig = null;
    private String broker = null;
    private static final int INIT_WAIT = 360;

    private boolean configureAndConnect() throws IOException {
        jenkins.configure();
        elasticSleep(5000);
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        if (msgConfig == null) {
            msgConfig = new ActiveMqMessagingProvider(ciPluginConfig).addMessagingProvider();
        }
        broker = amq1.getBroker();
        msgConfig.name("test")
            .broker(createFailoverUrl(broker))
            .topic("CI")
            .userNameAuthentication("admin", "redhat");

        int counter = 0;
        while (counter < INIT_WAIT) {
            try {
                msgConfig.testConnection();
                waitFor(driver, hasContent("Successfully connected to " + createFailoverUrl(amq1.getBroker())), 5);
                elasticSleep(1000);
                jenkins.save();
                return true;
            } catch (Exception e) {
                counter++;
                elasticSleep(1000);
            }
        }
        return false;
    }
    
    @Before public void setUp() throws Exception {
        Plugin plugin = jenkins.getPlugin("dumpling");
        assertNotNull(plugin);

        amq1 = docker1.get();
        if (!configureAndConnect()) {
            throw new Exception("Did not get connection successful message in " + INIT_WAIT + " secs.");
        }
    }

    private String createFailoverUrl(String broker) {
        return "failover:(" + broker + "," + broker + ")?startupMaxReconnectAttempts=1&maxReconnectAttempts=1";
    }

    @Test
    public void testGlobalConfigTestConnection() throws Exception {
    }

    @Test
    public void testSimpleCIEventTrigger() throws Exception {
        ArrayList<FreeStyleJob> jobs = new ArrayList<FreeStyleJob>();

        for (int i = 0 ; i < 10 ; i++) {
            FreeStyleJob jobA = jenkins.jobs.create(FreeStyleJob.class, "receiver" + i);
            jobA.configure();
            jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
            CIEventTrigger ciEvent = new CIEventTrigger(jobA);
            ProviderData pd = ciEvent.addProviderData();
            pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
            jobA.save();
            jobs.add(jobA);
        }
        elasticSleep(10000);
        waitForNoAMQTaskThreads();

        int currentThreadCount = getCurrentAMQThreadCount();
        int previousThreadCount = currentThreadCount;
        System.out.println("Current AMQ Thread Count: " + currentThreadCount);
        String previousThreads = printAMQThreads();
        System.out.println(previousThreads);


        FreeStyleJob jobB = jenkins.jobs.create(FreeStyleJob.class, "sender");
        jobB.configure();
        CINotifierPostBuildStep notifier = jobB.addPublisher(CINotifierPostBuildStep.class);
        notifier.messageType.select("CodeQualityChecksDone");
        notifier.messageProperties.sendKeys("CI_STATUS = failed");
        jobB.save();
        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        for (FreeStyleJob job: jobs) {
            job.getLastBuild().shouldSucceed().shouldExist();
            assertThat(job.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
            job.getLastBuild().delete();
        }

        //Now stop AMQ
        System.out.println("Stopping AMQ");
        stopAMQ();
        System.out.println("Waiting 60 secs");
        elasticSleep(60000);

        //Check for unconnection AMQ threads
        System.out.println(printAMQThreads());
        ensureNoUnconnectedThreads();

        //Now startup
        System.out.println("Starting AMQ");
        startAMQ();

        // after restart the ports may have changed (likely)
        if (!amq1.getBroker().equals(broker)) {
            configureAndConnect();
            // re-save the jobs because the AMQ ports may have changed
            for (FreeStyleJob job : jobs) {
                job.configure();
                job.save();
            }
            jobB.configure();
            jobB.save();
        }

        System.out.println("Waiting 10 secs");
        elasticSleep(10000);
        waitForNoAMQTaskThreads();

        System.out.println(printAMQThreads());
        ensureNoLeakingThreads(previousThreadCount, previousThreads);

        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        for (FreeStyleJob job: jobs) {
            job.getLastBuild().shouldSucceed().shouldExist();
            assertThat(job.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
        }
        System.out.println(printAMQThreads());
        System.out.println("Waiting 10 secs");
        elasticSleep(10000);
        waitForNoAMQTaskThreads();
    }

    @Test
    public void testInvalidJMSSelector() throws Exception {
        // Test setting a valid JMS selector, then fixing it, and make sure threads are handled correctly.

        FreeStyleJob jobA = jenkins.jobs.create(FreeStyleJob.class, "receiver");
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE 'code-quality-checks-done' and CI_STATUS = 'failed'");  // Missing '='; invalid syntax.
        jobA.apply();
        elasticSleep(5000);

        ArrayList<Integer> ids1 = getCurrentTriggerThreadIds("receiver");
        assertTrue("Trigger threads invalid syntax size", ids1.size() == 0);
        jobA.open();
        assertTrue(driver.getPageSource().contains("CI Build Trigger Issue"));
        assertTrue(driver.getPageSource().contains("javax.jms.InvalidSelectorException"));

        //Now fix the selector.
        jobA.configure();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.save();
        elasticSleep(5000);

        ArrayList<Integer> ids2 = getCurrentTriggerThreadIds("receiver");
        assertTrue("Trigger threads valid selector size", ids2.size() == 1);
    }

    @WithPlugins("workflow-aggregator")
    @Test
    public void testInvalidJMSSelectorInPipeline() throws Exception {
        // Test setting a valid JMS selector in a pipeline, then fixing it, and make sure threads are handled correctly.

        WorkflowJob pipe = jenkins.jobs.create(WorkflowJob.class, "pipeline");
        pipe.script.set(
                "pipeline {\n" +
                "    agent { label 'master' }\n" +
                "    triggers {\n" +
                "        ciBuildTrigger(noSquash: true,\n" +
                "                       providerData: activeMQSubscriber(name: 'test',\n" +
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
                "}\n"
        );
        pipe.save();
        elasticSleep(5000);

        // No trigger threads created on save. Must run once.
        ArrayList<Integer> ids = getCurrentTriggerThreadIds("pipeline");
        assertTrue("Trigger threads initial pipeline save", ids.size() == 0);

        pipe.startBuild().shouldSucceed();
        elasticSleep(5000);
        // No trigger threads created because of bad syntax.
        ArrayList<Integer> ids2 = getCurrentTriggerThreadIds("pipeline");
        assertTrue("Trigger threads invalid syntax size", ids2.size() == 0);

        pipe.open();
        assertTrue(driver.getPageSource().contains("CI Build Trigger Issue"));
        assertTrue(driver.getPageSource().contains("javax.jms.InvalidSelectorException"));

        //Now fix the selector.
        pipe.configure();
        pipe.script.set(
                "pipeline {\n" +
                "    agent { label 'master' }\n" +
                "    triggers {\n" +
                "        ciBuildTrigger(noSquash: true,\n" +
                "                       providerData: activeMQSubscriber(name: 'test',\n" +
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
                "}\n"
        );
        pipe.save();
        elasticSleep(5000);

        // Let's start a build to get new selector activated.
        pipe.startBuild().shouldSucceed();
        elasticSleep(5000);
        ArrayList<Integer> ids5 = getCurrentTriggerThreadIds("pipeline");
        System.err.println("ids5: " + ids5.size());
        assertTrue("Trigger threads valid selector size: " + ids5.size(), ids5.size() == 1);
    }

    @Test
    public void testChangingJMSSelector() throws Exception {
        // Test changing a selector and make sure threads are handled correctly.

        FreeStyleJob jobA = jenkins.jobs.create(FreeStyleJob.class, "receiver");
        jobA.configure();
        jobA.addShellStep("echo CI_TYPE = $CI_TYPE");
        CIEventTrigger ciEvent = new CIEventTrigger(jobA);
        ProviderData pd = ciEvent.addProviderData();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
        jobA.apply();
        elasticSleep(5000);

        ArrayList<Integer> ids1 = getCurrentTriggerThreadIds("receiver");
        assertTrue("Trigger threads valud selector size", ids1.size() == 1);

        //Now change the selector.
        jobA.configure();
        pd.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'passed'");
        jobA.save();
        elasticSleep(5000);

        ArrayList<Integer> ids2 = getCurrentTriggerThreadIds("receiver");
        assertTrue("Trigger threads changed selector size", ids2.size() == 1);
        assertTrue("Trigger threads new thread created", ids1.get(0) != ids2.get(0));
    }

    @SuppressWarnings("unchecked")
    private ArrayList<Integer> getCurrentTriggerThreadIds(String name) {
        String script = "Set<Integer> ids = new TreeSet<Integer>();\n" +
                "for (thread in D.runtime.threads.grep { it.name =~ /^CIBuildTrigger-" + name + "/ }) {\n" +
                "  ids.add(thread.getId());\n" +
                "}\n" +
                "return ids;";

        ObjectMapper m = new ObjectMapper();
        try {
            return m.readValue(jenkins.runScript(script), ArrayList.class);
        } catch (Exception e) {
        }
        return new ArrayList<>();
    }

    private int getCurrentAMQThreadCount() {
        String threadCount =
                jenkins.runScript("println D.runtime.threads.grep { it.name =~ /^ActiveMQ Transport/ }.size()");
        return Integer.parseInt(threadCount.trim());
    }

    private void startAMQ() throws Exception {
        System.out.println(Docker.cmd("restart", amq1.getCid()));
        System.out.println(Docker.cmd("restart", amq1.getCid())
                .popen()
                .verifyOrDieWith("Unable to start container"));
        elasticSleep(3000);
        amq1.assertRunning();
    }

    private void ensureNoUnconnectedThreads() {
        String threads = jenkins.runScript("println D.runtime.threads");
        boolean found = threads.indexOf("ActiveMQ Connection Executor: unconnected") >= 0;
        assertFalse("Threads called \"ActiveMQ Connection Executor: unconnected\" were found!", found);
    }

    private void waitForNoAMQTaskThreads() {
        String script = "import java.util.*\n" +
                "import java.util.regex.*\n" +
                "import com.github.olivergondza.dumpling.model.ThreadSet;\n" +
                "import static com.github.olivergondza.dumpling.model.ProcessThread.nameContains;\n" +
                "ThreadSet ts =  D.runtime.threads.where(nameContains(Pattern.compile(\"ActiveMQ.*Task-\")))\n" +
                "println(ts.size())";
        String threadCount = jenkins.runScript(script);
        int currentThreadCount = Integer.parseInt(threadCount.trim());
        int counter = 0;
        int MAXWAITTIME = 60;
        while (currentThreadCount != 0 && counter < MAXWAITTIME ) {
            System.out.println("currentThreadCount != 0");
            System.out.println(currentThreadCount + " != " + 0);
            elasticSleep(1000);
            counter++;
            threadCount = jenkins.runScript(script);
            currentThreadCount = Integer.parseInt(threadCount.trim());
        }
        boolean equal = currentThreadCount == 0;
        assertTrue("currentThreadCount != 0", equal);
        System.out.println("currentThreadCount == 0");
    }

    private String printAMQThreads() {
        String script = "import java.util.*\n" +
                "import com.github.olivergondza.dumpling.model.ThreadSet;\n" +
                "import static com.github.olivergondza.dumpling.model.ProcessThread.nameContains;\n" +
                "ThreadSet ts =  D.runtime.threads.where(nameContains(\"ActiveMQ Transport\"))\n" +
                "println(\"Filtered Thread Size: \" + ts.size());\n" +
                "Iterator it = ts.iterator();\n" +
                "while (it.hasNext()) {\n" +
                "  println(it.next().name)\n" +
                "}";
        String threads = jenkins.runScript(script);
        return threads;
    }
    private void ensureNoLeakingThreads(int previousThreadCount, String previousThreads) {
        int currentThreadCount = getCurrentAMQThreadCount();
        System.out.println("Current AMQ Thread Count: " + currentThreadCount);
        int counter = 0;
        int MAXWAITTIME = 60;
        while (abs(currentThreadCount- previousThreadCount) > 2 &&
                counter < MAXWAITTIME ) {
            System.out.println("abs(currentThreadCount [" + currentThreadCount
                    + "] - previousThreadCount [" + previousThreadCount + "] ) > 2");
            System.out.println(abs(currentThreadCount - previousThreadCount));
            elasticSleep(1000);
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
        System.out.println(Docker.cmd("stop", amq1.getCid()));
        System.out.println(Docker.cmd("stop", amq1.getCid())
                .popen()
                .verifyOrDieWith("Unable to stop container"));
        elasticSleep(3000);
        boolean running = false;
        try {
            amq1.assertRunning();
            running = false;
        }
        catch (Error e) {
            //This is ok
        }
        if (running) {
            throw new Exception("Container " + amq1.getCid() + " not stopped");
        }
    }

}
