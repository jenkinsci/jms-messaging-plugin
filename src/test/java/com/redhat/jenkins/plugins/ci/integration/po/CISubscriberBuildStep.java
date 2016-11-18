package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.AbstractStep;
import org.jenkinsci.test.acceptance.po.BuildStep;
import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;

@Describable("CI Subscriber")
public class CISubscriberBuildStep extends AbstractStep implements BuildStep {

    public final Control selector = control("selector");
    public final Control variable = control("variable");
    public final Control timeout = control("timeout");

    public CISubscriberBuildStep(Job parent, String path) {
        super(parent, path);
    }
}
