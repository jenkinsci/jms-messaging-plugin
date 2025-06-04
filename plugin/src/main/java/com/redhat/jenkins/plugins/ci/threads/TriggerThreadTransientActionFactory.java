package com.redhat.jenkins.plugins.ci.threads;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;

import hudson.Extension;
import hudson.model.Job;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;

@SuppressWarnings("rawtypes")
@Extension
public class TriggerThreadTransientActionFactory extends TransientActionFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @Override
    public @Nonnull Collection<TriggerThreadProblemAction> createFor(@Nonnull Job target) {
        CIBuildTrigger cibt = ParameterizedJobMixIn.getTrigger((Job<?, ?>) target, CIBuildTrigger.class);
        if (cibt != null) {
            return cibt.getJobActions();
        }
        return Collections.emptyList();
    }
}
