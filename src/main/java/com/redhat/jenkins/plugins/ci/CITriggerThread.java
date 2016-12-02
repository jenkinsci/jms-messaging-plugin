package com.redhat.jenkins.plugins.ci;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.MessagingWorker;
import hudson.security.ACL;

import java.util.logging.Logger;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

public class CITriggerThread implements Runnable {
    private static final Logger log = Logger.getLogger(CITriggerThread.class.getName());

    private static final Integer WAIT_HOURS = 1;
    private static final Integer WAIT_SECONDS = 2;

    private final MessagingWorker messagingWorker;
    private final String jobname;
    private final String selector;

    public CITriggerThread(MessagingProvider messagingProvider,
                           String jobname, String selector) {
        this.jobname = jobname;
        this.selector = selector;
        this.messagingWorker = messagingProvider.createWorker(this.jobname);
    }

    public void run() {
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (messagingWorker.subscribe(jobname, selector)) {
                    messagingWorker.receive(jobname, WAIT_HOURS * 60 * 60 * 1000);
                } else {
                    // Should not get here unless subscribe failed. This could be
                    // because global configuration may not yet be available or
                    // because we were interrupted. If not the latter, let's sleep
                    // for a bit before retrying.
                    if (!Thread.currentThread().isInterrupted()) {
                        try {
                            Thread.sleep(WAIT_SECONDS * 1000);
                        } catch (InterruptedException e) {
                            // We were interrupted while waiting to retry. We will
                            // jump ship on the next iteration.

                            // NB: The interrupt flag was cleared when
                            // InterruptedException was thrown. We have to
                            // re-install it to make sure we eventually leave this
                            // thread.
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            log.info("Shutting down trigger thread for job '" + jobname + "'.");
            messagingWorker.unsubscribe(jobname);
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }
}
