package com.redhat.jenkins.plugins.ci.messaging;

import hudson.EnvVars;
import hudson.model.TaskListener;

import java.util.List;

import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;

public abstract class JMSMessageWatcher {

    protected String jobname;
    protected int timeout;
    protected MessagingProviderOverrides overrides;
    protected String selector;
    protected List<MsgCheck> checks;
    protected JMSMessagingProvider provider;
    protected EnvVars environment;
    protected TaskListener taskListener;

    public JMSMessageWatcher(String jobname) {
        this.jobname = jobname;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setOverrides(MessagingProviderOverrides overrides) {
        this.overrides = overrides;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public void setChecks(List<MsgCheck> checks) {
        this.checks = checks;
    }

    public abstract String watch();

    public static String getTopic(MessagingProviderOverrides overrides, String providerTopic, String defaultTopic) {
        if (overrides != null && overrides.getTopic() != null && !overrides.getTopic().isEmpty()) {
            return overrides.getTopic();
        } else if (providerTopic != null && !providerTopic.isEmpty()) {
            return providerTopic;
        } else {
            return defaultTopic;
        }
    }

    public void setProvider(JMSMessagingProvider messagingProvider) {
        this.provider = messagingProvider;
    }

    public abstract void interrupt();

    public void setEnvironment(EnvVars environment) {
        this.environment = environment;
    }

    public void setTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
    }
}
