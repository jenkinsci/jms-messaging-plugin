package com.redhat.jenkins.plugins.ci;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.ParameterValue;
import hudson.model.AbstractBuild;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CIEnvironmentContributingAction implements EnvironmentContributingAction {

    private transient Map<String, String> messageParams;
    private transient Set<String> jobParams = new HashSet<String>();

    public CIEnvironmentContributingAction(Map<String, String> messageParams) {
        this(messageParams, null);
    }

    public CIEnvironmentContributingAction(Map<String, String> mParams, List<ParameterValue> jParams) {
        this.messageParams = mParams;
        if (jParams != null) {
            for (ParameterValue pv : jParams) {
                this.jobParams.add(pv.getName());
            }
        }
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
         return null;
    }

    public String getUrlName() {
        return null;
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {

        if (env == null || messageParams == null) {
            return;
        }

        // Only include variables in environment that are not defined as job parameters.
        for (String key : messageParams.keySet()) {
            if (!jobParams.contains(key)) {
                env.put(key, messageParams.get(key));
            }
        }
    }
}
