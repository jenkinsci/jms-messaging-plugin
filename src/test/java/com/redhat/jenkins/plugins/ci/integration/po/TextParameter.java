package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.Parameter;

/**
 * @author Kohsuke Kawaguchi
 */
@Describable("Multi-line String Parameter")
public class TextParameter extends Parameter {
    public TextParameter(Job job, String path) {
        super(job, path);
    }

    @Override
    public void fillWith(Object v) {
        control("value").set(v.toString());
    }
}
