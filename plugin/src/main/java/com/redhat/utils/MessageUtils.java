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
package com.redhat.utils;

import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

public class MessageUtils {

    private static final Logger log = Logger.getLogger(MessageUtils.class.getName());

    public static final String JSON_TYPE = "application/json";

    public static ListBoxModel doFillProviderNameItems() {
        ListBoxModel items = new ListBoxModel();
        for (JMSMessagingProvider provider : GlobalCIConfiguration.get().getConfigs()) {
            items.add(provider.getName());
        }
        return items;
    }

    public static SendResult sendMessage(Run<?, ?> run, TaskListener listener, ProviderData pdata) {
        String startMessage = "Sending message for job '" + run.getParent().getName() + "'.";
        log.info(startMessage);
        listener.getLogger().println(startMessage);
        GlobalCIConfiguration config = GlobalCIConfiguration.get();
        JMSMessagingProvider provider = config.getProvider(pdata.getName());
        if (provider != null) {
            JMSMessagingWorker worker = provider.createWorker(pdata, run.getParent().getName());
            SendResult sendResult = worker.sendMessage(run, listener, pdata);
            if (sendResult.isSucceeded()) {
                String completedMessage = "Sent successfully with messageId: " + sendResult.getMessageId();
                log.info(completedMessage);
                listener.getLogger().println(completedMessage);
            } else {
                String failedMessage = "Failed to send message with messageId: " + sendResult.getMessageId();
                log.info(failedMessage);
                listener.getLogger().println(failedMessage);
            }
            return sendResult;
        } else {
            String errorMessage = "Unable to find provider " + pdata.getName() + ".";
            log.severe(errorMessage);
            listener.getLogger().println(errorMessage);
            return null;
        }

    }
}
