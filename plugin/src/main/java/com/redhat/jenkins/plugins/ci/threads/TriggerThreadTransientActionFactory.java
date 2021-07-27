package com.redhat.jenkins.plugins.ci.threads;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import hudson.Extension;
import hudson.model.Job;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

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
