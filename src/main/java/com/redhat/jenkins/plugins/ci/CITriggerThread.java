package com.redhat.jenkins.plugins.ci;

import hudson.security.ACL;

import java.util.Objects;
import java.util.logging.Logger;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang3.builder.EqualsBuilder;

import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
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

    private static final Integer WAIT_MINUTES = 60;
    private static final Integer WAIT_SECONDS = 2;

    private final JMSMessagingProvider messagingProvider;
    private final String jobname;
    private final ProviderData providerData;
    private final JMSMessagingWorker messagingWorker;

    public CITriggerThread(JMSMessagingProvider messagingProvider, ProviderData providerData, String jobname, int instance) {
        this.messagingProvider = messagingProvider;
        this.providerData = providerData;
        this.jobname = jobname;
        Objects.requireNonNull(messagingProvider);
        this.messagingWorker = messagingProvider.createWorker(providerData, this.jobname);

        setName("CIBuildTrigger-" + jobname + "-" + instance + "-" + messagingProvider.getClass().getSimpleName());
        setDaemon(true);
    }

    public void sendInterrupt() {
        messagingWorker.prepareForInterrupt();
    }

    public void run() {
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            while (!Thread.currentThread().isInterrupted() && !messagingWorker.isBeingInterrupted()) {
                messagingWorker.receive(jobname, providerData);
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
                .append(providerData, thread.providerData)
                .append(jobname, thread.jobname)
                .isEquals();
    }

    @Override
    public int hashCode() {
        int result = messagingWorker != null ? messagingWorker.hashCode() : 0;
        result = 31 * result + (providerData != null ? providerData.hashCode() : 0);
        result = 31 * result + (jobname != null ? jobname.hashCode() : 0);
        return result;
    }

}
