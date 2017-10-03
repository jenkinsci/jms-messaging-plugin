package com.redhat.jenkins.plugins.ci.messaging.data;

public class SendResult {

    private boolean succeeded;
    private Object message;

    public SendResult(boolean succeeded, Object message) {
        this.succeeded = succeeded;
        this.message = message;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public void setSucceeded(boolean succeeded) {
        this.succeeded = succeeded;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }
}
