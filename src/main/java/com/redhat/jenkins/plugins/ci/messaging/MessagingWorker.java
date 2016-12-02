package com.redhat.jenkins.plugins.ci.messaging;

import com.redhat.utils.MessageUtils;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Created by shebert on 29/11/16.
 */
public abstract class MessagingWorker {
    public String jobname;

    public abstract boolean subscribe(String jobname, String selector);
    public abstract void unsubscribe(String jobname);
    public abstract void receive(String jobname, long timeoutInMs);
    public abstract boolean connect() throws Exception;
    public abstract boolean isConnected();
    public abstract void disconnect();

    public abstract boolean sendMessage(Run<?, ?> build,
                                     TaskListener listener,
                                     MessageUtils.MESSAGE_TYPE type,
                                     String props,
                                     String content);

    public abstract String waitForMessage(Run<?, ?> build, String selector,
                                          String variable, Integer timeout);
}

