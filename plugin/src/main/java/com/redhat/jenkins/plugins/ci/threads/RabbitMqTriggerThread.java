package com.redhat.jenkins.plugins.ci.threads;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingWorker;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RabbitMqTriggerThread extends CITriggerThread {
    private static final Logger log = Logger.getLogger(RabbitMqTriggerThread.class.getName());

    private RabbitMQMessagingWorker worker;

    public RabbitMqTriggerThread(JMSMessagingProvider messagingProvider, ProviderData providerData, String jobname, CIBuildTrigger cibt, int instance) {
        super(messagingProvider, providerData, jobname, cibt, instance);
        worker = (RabbitMQMessagingWorker)messagingWorker;
    }

    @Override
    public void shutdown() {
        try {
            worker.prepareForInterrupt();
            interrupt();
            join();
        } catch (Exception e) {
            log.log(Level.WARNING, "Unhandled exception in RabbitMQ trigger stop.", e);
        }
    }

    public boolean hasBeenInterrupted() {
        return !Thread.currentThread().isInterrupted() && worker.isBeingInterrupted();
    }
}
