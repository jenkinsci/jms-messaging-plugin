package com.redhat.jenkins.plugins.ci.messaging.data;

import java.io.Serializable;

public class SendResult implements Serializable {

    private boolean succeeded;
    private String messageId;
    private String messageContent;

    public SendResult(boolean succeeded, String messageId, String messageContent) {
        this.succeeded = succeeded;
        this.messageId = messageId;
        this.messageContent = messageContent;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public void setSucceeded(boolean succeeded) {
        this.succeeded = succeeded;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }
}
