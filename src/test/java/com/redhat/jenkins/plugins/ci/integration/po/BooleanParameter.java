package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.Parameter;

/**
 * @author Kohsuke Kawaguchi
 */
@Describable("Boolean Parameter")
public class BooleanParameter extends Parameter {
    public BooleanParameter(Job job, String path) {
        super(job, path);
    }

    @Override
    public void fillWith(Object v) {
        control("value").set(v.toString());
    }

    public void setDefault(boolean defaultValue) {
        control("defaultValue").check(defaultValue);
    }
}
