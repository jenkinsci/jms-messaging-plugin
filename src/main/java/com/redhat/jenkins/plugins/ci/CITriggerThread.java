package com.redhat.jenkins.plugins.ci;

import hudson.security.ACL;

import java.util.List;
import java.util.logging.Logger;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang3.builder.EqualsBuilder;

import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.FedMsgSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

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
public class CITriggerThread extends Thread {
    private static final Logger log = Logger.getLogger(CITriggerThread.class.getName());

    private static final Integer WAIT_HOURS = 1;
    private static final Integer WAIT_SECONDS = 2;

    private final JMSMessagingProvider messagingProvider;
    private final MessagingProviderOverrides overrides;
    private final JMSMessagingWorker messagingWorker;
    private final String jobname;
    private final String selector;
    private final List<MsgCheck> checks;

    public CITriggerThread(JMSMessagingProvider messagingProvider, String jobname, ProviderData tp) {
        this.messagingProvider = messagingProvider;
        this.jobname = jobname;
        if (tp instanceof ActiveMQSubscriberProviderData) {
            ActiveMQSubscriberProviderData atp = (ActiveMQSubscriberProviderData)tp;
            this.overrides = atp.getOverrides();
            this.selector = atp.getSelector();
            this.checks = atp.getChecks();
        } else if (tp instanceof FedMsgSubscriberProviderData) {
            FedMsgSubscriberProviderData ftp = (FedMsgSubscriberProviderData)tp;
            this.overrides = ftp.getOverrides();
            this.selector = null;
            this.checks = ftp.getChecks();
        } else {
            throw new RuntimeException("Unknown TriggerProvider.");
        }
        this.messagingWorker = messagingProvider.createWorker(overrides, this.jobname);
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

        CITriggerThread thread = (CITriggerThread) o;

        return new EqualsBuilder()
                .append(messagingProvider, thread.messagingProvider)
                .append(overrides, thread.overrides)
                .append(jobname, thread.jobname)
                .append(selector, thread.selector)
                .append(checks, thread.checks)
                .isEquals();
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
