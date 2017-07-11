package com.redhat.jenkins.plugins.ci;

import hudson.security.ACL;

import java.util.List;
import java.util.logging.Logger;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;

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
 */public class CITriggerThread extends Thread {
    private static final Logger log = Logger.getLogger(CITriggerThread.class.getName());

    private static final Integer WAIT_HOURS = 1;
    private static final Integer WAIT_SECONDS = 2;

    private final JMSMessagingWorker messagingWorker;
    private final String jobname;
    private final String selector;
    private final List<MsgCheck> checks;

    public CITriggerThread(JMSMessagingProvider messagingProvider, MessagingProviderOverrides overrides,
                           String jobname, String selector, List<MsgCheck> checks) {
        this.jobname = jobname;
        this.selector = selector;
        this.messagingWorker = messagingProvider.createWorker(overrides, this.jobname);
        this.checks = checks;
    }

    public void sendInterrupt() {
        messagingWorker.prepareForInterrupt();
    }

    public void run() {
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            while (!Thread.currentThread().isInterrupted() && !messagingWorker.isBeingInterrupted()) {
                messagingWorker.receive(jobname, selector, checks, WAIT_HOURS * 60 * 60 * 1000);
            }
            log.info("Shutting down trigger thread for job '" + jobname + "'.");
            messagingWorker.unsubscribe(jobname);
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

    public boolean isMessageProviderConnected() {
        if (messagingWorker == null) return false;
        return messagingWorker.isConnectedAndSubscribed();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CITriggerThread that = (CITriggerThread) o;

        if (jobname != null ? !jobname.equals(that.jobname) : that.jobname != null) return false;
        if (selector != null ? !selector.equals(that.selector) : that.selector != null) return false;
        return checks != null ? checks.equals(that.checks) : that.checks == null;
    }

    @Override
    public int hashCode() {
        int result = messagingWorker != null ? messagingWorker.hashCode() : 0;
        result = 31 * result + (jobname != null ? jobname.hashCode() : 0);
        result = 31 * result + (selector != null ? selector.hashCode() : 0);
        result = 31 * result + (checks != null ? checks.hashCode() : 0);
        return result;
    }

}
