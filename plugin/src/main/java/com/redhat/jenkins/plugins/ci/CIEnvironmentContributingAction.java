package com.redhat.jenkins.plugins.ci;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.model.AbstractBuild;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
    public void buildEnvironment(Run<?, ?> run, EnvVars env) {
	addEnvVars(env);
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
	addEnvVars(env);
    }

    private void addEnvVars(EnvVars env) {

	if (env == null || messageParams == null) {
	    return;
	}

	// Only include variables in environment that are not defined as job parameters. And
	// do not overwrite any existing environment variables (like PATH).
	for (String key : messageParams.keySet()) {
	    if (!jobParams.contains(key) && !env.containsKey(key)) {
		env.put(key, messageParams.get(key));
	    }
	}
    }
}
