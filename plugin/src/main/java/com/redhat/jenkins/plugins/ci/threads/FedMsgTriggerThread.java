package com.redhat.jenkins.plugins.ci.threads;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.messaging.FedMsgMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

public class FedMsgTriggerThread extends CITriggerThread {
    private static final Logger log = Logger.getLogger(FedMsgTriggerThread.class.getName());

    FedMsgMessagingWorker fworker;

    protected FedMsgTriggerThread(JMSMessagingProvider messagingProvider, ProviderData providerData, String jobname, CIBuildTrigger cibt, int instance) {
        super(messagingProvider, providerData, jobname, cibt, instance);
        fworker = (FedMsgMessagingWorker)messagingWorker;
    }

    @Override
    public void shutdown() {
        try {
            int waitCount = 0;
            while (waitCount <= 60 && !fworker.hasPoller()) {
                log.info("Thread " + getId() + ": FedMsg Provider is NOT connected AND subscribed. Sleeping 1 sec");
                Thread.sleep(1000);
                waitCount++;
            }
            if (waitCount > 60) {
                log.warning("Wait time of 60 secs elapsed trying to connect before interrupting...");
            }
            fworker.prepareForInterrupt();
            interrupt();
            if (fworker.hasPoller()) {
                log.info("Thread " + getId() + ": FedMsg Provider is connected and subscribed");
                log.info("Thread " + getId() + ": trying to join");
                join();
            } else {
                log.warning("Thread " + getId() + " FedMsg Provider is NOT connected AND subscribed;  join!");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception in FedMsg trigger stop.", e);
        }
    }

    public boolean hasBeenInterrupted() {
        return !Thread.currentThread().isInterrupted() && fworker.isBeingInterrupted();
    }
}
