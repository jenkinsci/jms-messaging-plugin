package com.redhat.jenkins.plugins.ci;

import hudson.model.Cause;

import org.kohsuke.stapler.export.Exported;

public class CIBuildCause extends Cause {

	@Override
	@Exported(visibility = 3)
	public String getShortDescription() {
		return "Triggered by CI message.";
	}
}