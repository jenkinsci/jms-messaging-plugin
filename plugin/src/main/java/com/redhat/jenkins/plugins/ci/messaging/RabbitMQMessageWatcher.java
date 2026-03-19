package com.redhat.jenkins.plugins.ci.messaging;

public class RabbitMQMessageWatcher extends JMSMessageWatcher {

    public RabbitMQMessageWatcher(String jobname) {
        super(jobname);
    }

    @Override
    public String watch() {
        return null;
    }

    @Override
    public void interrupt() {

    }
}
