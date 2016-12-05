package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.PageAreaImpl;

@Describable("CI event")
public class CIEventTrigger extends PageAreaImpl {
    public final Control providerName = control("providerName");
    public final Control selector = control("selector");

    public CIEventTrigger(Job parent) {
        super(parent, "/com-redhat-jenkins-plugins-ci-CIBuildTrigger");
        control("").check();
    }

}
