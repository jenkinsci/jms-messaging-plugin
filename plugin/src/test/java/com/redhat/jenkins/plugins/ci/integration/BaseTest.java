package com.redhat.jenkins.plugins.ci.integration;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.security.ACLContext;

public abstract class BaseTest {
    private static final Logger log = Logger.getLogger(BaseTest.class.getName());

    @Rule
    public final JenkinsRule j = new JenkinsRule();
    @Rule
    public final LoggerRule logger = new LoggerRule();
    @Rule
    public TestName testName = new TestName();

    @After
    public void after() throws IOException, InterruptedException {
        if (j != null && j.jenkins != null) {
            for (Project p : j.jenkins.getProjects()) {
                p.delete();
            }
        }
    }

    public void addUsernamePasswordCredential(String id, String username, String password) throws Exception {
        UsernamePasswordCredentialsImpl cred = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id, "",
                username, password);

        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            SystemCredentialsProvider provider = SystemCredentialsProvider.getInstance();
            List<Credentials> creds = provider.getCredentials();
            creds.add(cred);
            provider.save();
        }
    }

    protected boolean waitForProviderToBeReady(String cid, String tag) throws Exception {
        ProviderDocker d = new ProviderDocker();
        for (Integer i = 0; i < 150; i++) {
            if (d.isContainerReady(cid, tag)) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    protected boolean waitForProviderToStop(String cid) throws Exception {
        ProviderDocker d = new ProviderDocker();
        if (d.stopContainer(cid)) {
            for (Integer i = 0; i < 150; i++) {
                if (!d.isContainerRunning(cid)) {
                    return true;
                }
                Thread.sleep(200);
            }
        }
        return false;
    }

    protected void waitUntilTriggeredBuildCompletes(FreeStyleProject job) throws Exception {
        waitUntilTriggeredBuildCompletes(job, 1);
    }

    protected void waitUntilTriggeredBuildCompletes(FreeStyleProject job, int number) throws Exception {
        for (Integer i = 0; i < 150; i++) {
            if (job.getBuildByNumber(number) != null && job.getBuildByNumber(number).getResult() != null) {
                return;
            }
            Thread.sleep(200);
        }
        throw new Exception("Triggered job '" + job.getFullName() + "' #" + number + " has not completed");
    }

    protected void waitUntilTriggeredBuildCompletes(WorkflowJob job) throws Exception {
        waitUntilTriggeredBuildCompletes(job, 1);
    }

    protected void waitUntilTriggeredBuildCompletes(WorkflowJob job, int number) throws Exception {
        for (Integer i = 0; i < 150; i++) {
            if (job.getBuildByNumber(number) != null && job.getBuildByNumber(number).getResult() != null) {
                return;
            }
            Thread.sleep(200);
        }
        throw new Exception("Triggered job '" + job.getFullName() + "' #" + number + " has not completed");
    }

    protected MessagingProviderOverrides overrideTopic(String topic) {
        return topic == null ? null : new MessagingProviderOverrides(topic);
    }

    protected CIBuildTrigger attachTrigger(CIBuildTrigger trigger, AbstractProject<?, ?> job) throws Exception {
        job.addTrigger(trigger);
        startTrigger(trigger, job);
        return trigger;
    }

    protected void startTrigger(CIBuildTrigger trigger, Job<?, ?> job) throws Exception {
        trigger.start(job, true);
        waitForReceiverToBeReady(job.getFullName(), trigger.getProviders().size());
    }

    protected CIBuildTrigger attachTrigger(CIBuildTrigger trigger, WorkflowJob job) throws Exception {
        job.addTrigger(trigger);
        startTrigger(trigger, job);
        return trigger;
    }

    protected void scheduleAwaitStep(WorkflowJob job) throws Exception {
        scheduleAwaitStep(job, 1);
    }

    protected void scheduleAwaitStep(WorkflowJob job, int occurrences) throws Exception {
        scheduleAwaitStep(job, occurrences, false);
    }

    protected void scheduleAwaitStep(WorkflowJob job, int occurrences, boolean skipCheck) throws Exception {
        WorkflowRun r = job.scheduleBuild2(0).waitForStart();
        waitForReceiverToBeReady(job.getFullName(), occurrences, skipCheck);
    }

    protected void scheduleAwaitStep(AbstractProject<?, ?> job) throws Exception {
        scheduleAwaitStep(job, 1);
    }

    protected void scheduleAwaitStep(AbstractProject<?, ?> job, int occurrences) throws Exception {
        Run<?, ?> r = job.scheduleBuild2(0).waitForStart();
        waitForReceiverToBeReady(job.getFullName(), occurrences);
    }

    protected void waitForReceiverToBeReady(String jobname) throws Exception {
        waitForReceiverToBeReady(jobname, 1);
    }

    protected void waitForReceiverToBeReady(String jobname, int occurrences) throws Exception, InterruptedException {
        waitForReceiverToBeReady(jobname, occurrences, false);
    }

    protected boolean additionalWaitForReceiverToBeReadyCheck(String jobname, int occurrences) {
        return true;
    }

    protected void waitForReceiverToBeReady(String jobname, int occurrences, boolean skipCheck)
            throws Exception, InterruptedException {
        String term = "Job '" + jobname + "' waiting to receive message";
        for (Integer i = 0; i < 150; i++) {
            Matcher<LoggerRule> m = logger.recorded(Level.INFO, Matchers.containsString(term));
            if (m.matches(logger) && Collections.frequency(logger.getMessages(), term) >= occurrences
                    && (skipCheck || additionalWaitForReceiverToBeReadyCheck(jobname, occurrences))) {
                return;
            }
            Thread.sleep(200);
        }
        throw new Exception("Receiver '" + jobname + "' is not ready");
    }
}
