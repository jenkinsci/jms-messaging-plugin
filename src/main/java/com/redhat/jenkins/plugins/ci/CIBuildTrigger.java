package com.redhat.jenkins.plugins.ci;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class CIBuildTrigger extends Trigger<AbstractProject<?, ?>> {
	private static final Logger log = Logger.getLogger(CIBuildTrigger.class.getName());

	private String selector;
	public static final transient WeakHashMap<String, Thread> triggerInfo = new WeakHashMap<String, Thread>();

	@DataBoundConstructor
	public CIBuildTrigger(String selector) {
		super();
		this.selector = StringUtils.stripToNull(selector);
	}

	@Override
	public void start(AbstractProject<?, ?> project, boolean newInstance) {
		super.start(project, newInstance);
		startTriggerThread();
	}

	@Override
	public void stop() {
		super.stop();
		stopTriggerThread();
	}

	private void startTriggerThread() {
        if (job.isDisabled()) {
            log.info("Job '" + job.getFullName() + "' is disabled, not subscribing.");
        } else {
            try {
                stopTriggerThread();
                Thread thread = new Thread(new CITriggerThread(job.getFullName(), selector));
                thread.start();
                triggerInfo.put(job.getFullName(), thread);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in trigger start.", e);
            }
        }
	}

	private void stopTriggerThread() {
	    Thread thread = triggerInfo.get(job.getFullName());
        if (thread != null) {
            try {
                thread.interrupt();
                thread.join();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in trigger stop.", e);
            }
        }
        triggerInfo.remove(job.getFullName());;
	}

	public String getSelector() {
		return selector;
	}

	public void setSelector(String selector) {
		this.selector = selector;
	}

	public void scheduleBuild(Map<String, String> messageParams) {
	    List<ParameterValue> definedParameters = getDefinedParameters(job);
	    List<ParameterValue> buildParameters = getUpdatedParameters(messageParams, definedParameters);
		job.scheduleBuild2(0, new CIBuildCause(), new ParametersAction(buildParameters), new CIEnvironmentContributingAction(messageParams, buildParameters));
	}

	private List<ParameterValue> getUpdatedParameters(Map<String, String> messageParams, List<ParameterValue> definedParams) {
	    // Update any build parameters that may have values from the triggering message.
	    HashMap<String, ParameterValue> newParams = new HashMap<String, ParameterValue>();
	    for (ParameterValue def : definedParams) {
	        newParams.put(def.getName(), def);
	    }
	    for (String key : messageParams.keySet()) {
	        if (newParams.containsKey(key)) {
	            StringParameterValue spv = new StringParameterValue(key, messageParams.get(key));
	            newParams.put(key, spv);
	        }
	    }
	    return new ArrayList<ParameterValue>(newParams.values());
	}

	private List<ParameterValue> getDefinedParameters(AbstractProject<?, ?> project) {
	    List<ParameterValue> parameters = new ArrayList<ParameterValue>();
	    ParametersDefinitionProperty properties = project.getProperty(ParametersDefinitionProperty.class);

	    if (properties != null  && properties.getParameterDefinitions() != null) {
	        for (ParameterDefinition paramDef : properties.getParameterDefinitions()) {
	            ParameterValue param = paramDef.getDefaultParameterValue();
	            if (param != null) {
	                parameters.add(param);
	            }
	        }
	    }
	    return parameters;
	}


	@Override
	public CIBuildTriggerDescriptor getDescriptor() {
	    return (CIBuildTriggerDescriptor) Jenkins.getInstance().getDescriptor(getClass());
	}

	@Extension
	public static class CIBuildTriggerDescriptor extends TriggerDescriptor {

	    public CIBuildTriggerDescriptor() {
	        super(CIBuildTrigger.class);
	    }

		@Override
		public boolean isApplicable(Item item) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return Messages.PluginName();
		}
	}
}
