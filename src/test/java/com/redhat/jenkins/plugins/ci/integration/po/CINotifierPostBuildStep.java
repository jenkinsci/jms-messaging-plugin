package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.AbstractStep;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.PostBuildStep;

@Describable("CI Notifier")
public class CINotifierPostBuildStep extends AbstractStep implements PostBuildStep {

    public final Control providerName = control("providerName");
    public final Control messageType = control("messageType");
    public final Control messageProperties = control("messageProperties");
    public final Control messageContent = control("messageContent");

    public CINotifierPostBuildStep(Job parent, String path) {
        super(parent, path);
    }
}
