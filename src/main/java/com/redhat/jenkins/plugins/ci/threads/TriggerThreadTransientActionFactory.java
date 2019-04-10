package com.redhat.jenkins.plugins.ci.threads;

import hudson.Extension;
import hudson.model.AbstractProject;

import java.util.Collection;
import java.util.Collections;

import jenkins.model.TransientActionFactory;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;

@SuppressWarnings("rawtypes")
@Extension
public class TriggerThreadTransientActionFactory extends TransientActionFactory {

    @Override
    public Class type() {
        return AbstractProject.class;
    }

    @Override
    public Collection createFor(Object target) {
        if (target instanceof AbstractProject) {
            AbstractProject project = (AbstractProject)target;
            @SuppressWarnings("unchecked")
            CIBuildTrigger cibt = (CIBuildTrigger)project.getTrigger(CIBuildTrigger.class);
            return cibt == null || cibt.getJobAction() == null ? Collections.emptyList() : Collections.singletonList(cibt.getJobAction());
        }
        return Collections.emptyList();
    }
}
