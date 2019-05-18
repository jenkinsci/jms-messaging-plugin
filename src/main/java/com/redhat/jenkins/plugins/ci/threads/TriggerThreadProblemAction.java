package com.redhat.jenkins.plugins.ci.threads;

import hudson.Functions;
import hudson.model.InvisibleAction;

public class TriggerThreadProblemAction extends InvisibleAction {
    private Exception exception;

    public TriggerThreadProblemAction(Exception exception) {
        this.setException(exception);
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public String getStackTrace() {
        return Functions.printThrowable(exception);
    }
}