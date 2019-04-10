package com.redhat.jenkins.plugins.ci.threads;

import hudson.model.InvisibleAction;

import java.io.PrintWriter;
import java.io.StringWriter;

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
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
}