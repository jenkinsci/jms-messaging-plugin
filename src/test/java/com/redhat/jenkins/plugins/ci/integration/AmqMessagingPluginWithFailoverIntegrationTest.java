package com.redhat.jenkins.plugins.ci.integration;

import com.google.inject.Inject;
import com.redhat.jenkins.plugins.ci.integration.docker.fixtures.JBossAMQContainer;
import com.redhat.jenkins.plugins.ci.integration.po.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.integration.po.CIEventTrigger;
import com.redhat.jenkins.plugins.ci.integration.po.CINotifierPostBuildStep;
import com.redhat.jenkins.plugins.ci.integration.po.GlobalCIConfiguration;
import org.jenkinsci.test.acceptance.docker.Docker;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Plugin;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static java.lang.StrictMath.abs;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.hasContent;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
@WithPlugins({"jms-messaging", "dumpling"})
@WithDocker
public class AmqMessagingPluginWithFailoverIntegrationTest extends AbstractJUnitTest {
    @Inject private DockerContainerHolder<JBossAMQContainer> docker;

    private JBossAMQContainer amq = null;
    private static final int INIT_WAIT = 360;

    @Before public void setUp() throws Exception {
        Plugin plugin = jenkins.getPlugin("dumpling");
        assertNotNull(plugin);

        amq = docker.get();
        jenkins.configure();
        elasticSleep(5000);
        GlobalCIConfiguration ciPluginConfig = new GlobalCIConfiguration(jenkins.getConfigPage());
        ActiveMqMessagingProvider msgConfig = new ActiveMqMessagingProvider(ciPluginConfig).addMessagingProvider();
        msgConfig.name("test")
            .broker(createFailoverUrl(amq.getBroker()))
            .topic("CI")
            .userNameAuthentication("admin", "redhat");

        int counter = 0;
        boolean connected = false;
        while (counter < INIT_WAIT) {
            try {
                msgConfig.testConnection();
                waitFor(driver, hasContent("Successfully connected to " + createFailoverUrl(amq.getBroker())), 5);
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
            ciEvent.selector.set("CI_TYPE = 'code-quality-checks-done' and CI_STATUS = 'failed'");
            jobA.save();
            jobs.add(jobA);
        }
        elasticSleep(10000);
        waitForNoAMQTaskThreads();

        int currentThreadCount = getCurrentAMQThreadCount();
        System.out.println("Current AMQ Thread Count: " + currentThreadCount);
        printAMQThreads();

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
        printAMQThreads();
        ensureNoUnconnectedThreads();

        //Now startup
        System.out.println("Starting AMQ");
        startAMQ();

        System.out.println("Waiting 10 secs");
        elasticSleep(10000);
        waitForNoAMQTaskThreads();

        printAMQThreads();
        ensureNoLeakingThreads(currentThreadCount);

        jobB.startBuild().shouldSucceed();

        elasticSleep(1000);
        for (FreeStyleJob job: jobs) {
            job.getLastBuild().shouldSucceed().shouldExist();
            assertThat(job.getLastBuild().getConsole(), containsString("echo CI_TYPE = code-quality-checks-done"));
        }
        printAMQThreads();
        System.out.println("Waiting 10 secs");
        elasticSleep(10000);
        waitForNoAMQTaskThreads();
    }

    private int getCurrentAMQThreadCount() {
        String threadCount =
                jenkins.runScript("println D.runtime.threads.grep { it.name =~ /^ActiveMQ / }.size()");
        return Integer.parseInt(threadCount.trim());
    }

    private void startAMQ() throws Exception {
        System.out.println(Docker.cmd("start", amq.getCid())
                .popen()
                .verifyOrDieWith("Unable to start container"));
        elasticSleep(3000);
        amq.assertRunning();
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
        while (currentThreadCount != 0 &&
                counter < MAXWAITTIME ) {
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

    private void printAMQThreads() {
        String script = "import java.util.*\n" +
                "import com.github.olivergondza.dumpling.model.ThreadSet;\n" +
                "import static com.github.olivergondza.dumpling.model.ProcessThread.nameContains;\n" +
                "ThreadSet ts =  D.runtime.threads.where(nameContains(\"ActiveMQ\"))\n" +
                "println(\"Filtered Thread Size: \" + ts.size());\n" +
                "Iterator it = ts.iterator();\n" +
                "while (it.hasNext()) {\n" +
                "  println(it.next().name)\n" +
                "}";
        String threads = jenkins.runScript(script);
        System.out.println(threads);

    }
    private void ensureNoLeakingThreads(int previousThreadCount) {
        int currentThreadCount = getCurrentAMQThreadCount();
        System.out.println("Current AMQ Thread Count: " + currentThreadCount);
        int counter = 0;
        int MAXWAITTIME = 60;
        while (abs(currentThreadCount- previousThreadCount) > 2 &&
                counter < MAXWAITTIME ) {
            System.out.println("abs(currentThreadCount- previousThreadCount) > 2");
            System.out.println(abs(currentThreadCount- previousThreadCount));
            elasticSleep(1000);
            counter++;
            currentThreadCount = getCurrentAMQThreadCount();
        }
        boolean equal = abs(currentThreadCount- previousThreadCount) <= 2;
        assertTrue("abs(currentThreadCount- previousThreadCount) > 2", equal);
    }

    private void stopAMQ() throws Exception {
        System.out.println(Docker.cmd("stop", amq.getCid())
                .popen()
                .verifyOrDieWith("Unable to stop container"));
        elasticSleep(3000);
        boolean running = false;
        try {
            amq.assertRunning();
            running = false;
        }
        catch (Error e) {
            //This is ok
        }
        if (running) {
            throw new Exception("Container " + amq.getCid() + " not stopped");
        }
    }

}
