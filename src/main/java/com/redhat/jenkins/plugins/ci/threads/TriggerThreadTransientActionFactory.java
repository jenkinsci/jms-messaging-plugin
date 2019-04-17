package com.redhat.jenkins.plugins.ci.threads;

import hudson.Extension;
import hudson.model.Job;

import java.util.Collection;
import java.util.Collections;

import jenkins.model.TransientActionFactory;
import jenkins.model.ParameterizedJobMixIn;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;

@SuppressWarnings("rawtypes")
@Extension
public class TriggerThreadTransientActionFactory extends TransientActionFactory {

    @Override
    public Class type() {
        return Job.class;
    }

    @Override
    public Collection createFor(Object target) {
        CIBuildTrigger cibt = ParameterizedJobMixIn.getTrigger((Job<?,?>) target, CIBuildTrigger.class);
        return cibt == null || cibt.getJobAction() == null ? Collections.emptyList() : Collections.singletonList(cibt.getJobAction());
    }
}
