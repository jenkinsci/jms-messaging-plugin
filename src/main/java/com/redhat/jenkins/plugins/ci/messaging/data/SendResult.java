package com.redhat.jenkins.plugins.ci.messaging.data;

import java.io.Serializable;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

public class SendResult implements Serializable {
    private static final long serialVersionUID = 7922547493388410558L;

    private boolean succeeded;
    private String messageId;
    private String messageContent;

    public SendResult(boolean succeeded, String messageContent) {
        this(succeeded, null, messageContent);
    }

    public SendResult(boolean succeeded, String messageId, String messageContent) {
        this.succeeded = succeeded;
        this.messageId = messageId;
        this.messageContent = messageContent;
    }

    @Whitelisted
    public boolean isSucceeded() {
        return succeeded;
    }

    public void setSucceeded(boolean succeeded) {
        this.succeeded = succeeded;
    }

    @Whitelisted
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    @Whitelisted
    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }
}
