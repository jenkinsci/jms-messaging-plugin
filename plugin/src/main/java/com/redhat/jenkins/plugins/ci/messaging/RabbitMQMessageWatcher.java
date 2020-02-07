package com.redhat.jenkins.plugins.ci.messaging;

import java.util.logging.Logger;

public class RabbitMQMessageWatcher extends JMSMessageWatcher {

    private static final Logger log = Logger.getLogger(RabbitMQMessageWatcher.class.getName());

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
