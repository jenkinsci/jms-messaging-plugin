package com.redhat.jenkins.plugins.ci.messaging;

import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.logging.Logger;

public class RabbitMQMessagingWorker extends JMSMessagingWorker {

    private static final Logger log = Logger.getLogger(RabbitMQMessagingWorker.class.getName());
    private final RabbitMQMessagingProvider provider;

    public RabbitMQMessagingWorker(JMSMessagingProvider messagingProvider, MessagingProviderOverrides overrides, String jobname) {
        super(messagingProvider, overrides, jobname);
        this.provider = (RabbitMQMessagingProvider) messagingProvider;
    }

    @Override
    public boolean subscribe(String jobname, String selector) {
        return false;
    }

    @Override
    public void unsubscribe(String jobname) {

    }

    @Override
    public void receive(String jobname, ProviderData pdata) {

    }

    @Override
    public boolean connect() throws Exception {
        return false;
    }

    @Override
    public void disconnect() {

    }

    @Override
    public SendResult sendMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        return null;
    }

    @Override
    public String waitForMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        return null;
    }

    @Override
    public String getDefaultTopic() {
        return null;
    }
}
