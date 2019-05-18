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
public class TriggerThreadTransientActionFactory extends TransientActionFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @Override
    public Collection<TriggerThreadProblemAction> createFor(Job target) {
        CIBuildTrigger cibt = ParameterizedJobMixIn.getTrigger((Job<?,?>) target, CIBuildTrigger.class);
        if (cibt != null) {
            return cibt.getJobActions();
        }
        return Collections.emptyList();
    }
}
