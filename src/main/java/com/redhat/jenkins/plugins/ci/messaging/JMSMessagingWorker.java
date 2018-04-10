package com.redhat.jenkins.plugins.ci.messaging;

import static com.redhat.jenkins.plugins.ci.CIBuildTrigger.findTrigger;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.utils.PluginUtils;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.utils.MessageUtils;

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
public abstract class JMSMessagingWorker {
    public static final String MESSAGECONTENTFIELD = "message-content" ;
    public String jobname;
    private static final Logger log = Logger.getLogger(JMSMessagingWorker.class.getName());
    public static final Integer RETRY_MINUTES = 1;

    protected MessagingProviderOverrides overrides;
    protected String topic;

    public abstract boolean subscribe(String jobname, String selector);
    public boolean subscribe(String jobname) {
        return subscribe(jobname, null);
    }
    public abstract void unsubscribe(String jobname);
    public abstract void receive(String jobname, String selector, List<MsgCheck> checks, long timeoutInMs);
    public void receive(String jobname, List<MsgCheck> checks, long timeoutInMs) {
        receive(jobname, null, checks, timeoutInMs);
    }
    public abstract boolean connect() throws Exception;
    public abstract boolean isConnected();

    public abstract boolean isConnectedAndSubscribed();

    public abstract void disconnect();

    public abstract SendResult sendMessage(Run<?, ?> build,
                                           TaskListener listener,
                                           MessageUtils.MESSAGE_TYPE type,
                                           String props,
                                           String content, boolean failOnError);

    public abstract String waitForMessage(Run<?, ?> build,
            TaskListener listener,
            String selector,
            String variable,
            List<MsgCheck> checks,
            Integer timeout);

    public String waitForMessage(Run<?, ?> build,
            TaskListener listener,
            String variable,
            List<MsgCheck> checks,
            Integer timeout) {
        return waitForMessage(build, listener, null, variable, checks, timeout);
    }

    public void trigger(String jobname, String messageSummary,
                        Map<String, String> params) {
        CIBuildTrigger trigger = findTrigger(jobname);
        if (trigger != null) {
            log.info("Scheduling job '" + jobname + "' based on message:\n" + messageSummary);
            trigger.scheduleBuild(params);
        } else {
            log.log(Level.WARNING, "Unable to find CIBuildTrigger for '" + jobname + "'.");
        }
    }

    public abstract void prepareForInterrupt();

    public abstract boolean isBeingInterrupted();

    protected String getTopic(JMSMessagingProvider provider) {
        String ltopic;
        if (overrides != null && overrides.getTopic() != null && !overrides.getTopic().isEmpty()) {
            ltopic = overrides.getTopic();
        } else if (provider.getTopic() != null && !provider.getTopic().isEmpty()) {
            ltopic = provider.getTopic();
        } else {
            ltopic = FedMsgMessagingWorker.DEFAULT_PREFIX;
        }
        return PluginUtils.getSubstitutedValue(ltopic, null);
    }
}

