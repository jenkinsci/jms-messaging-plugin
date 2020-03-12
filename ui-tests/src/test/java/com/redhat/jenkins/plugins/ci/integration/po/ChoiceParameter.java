package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.Parameter;

/**
 * @author Kohsuke Kawaguchi
 */
@Describable("Choice Parameter")
public class ChoiceParameter extends Parameter {
    public ChoiceParameter(Job job, String path) {
        super(job, path);
    }

    @Override
    public void fillWith(Object v) {
        control("value").set(v.toString());
    }

    public void setChoices(String choices) {
        control("choices").set(choices);
    }

}
