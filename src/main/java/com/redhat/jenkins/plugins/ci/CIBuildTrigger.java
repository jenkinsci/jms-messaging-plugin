package com.redhat.jenkins.plugins.ci;

import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;

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
public class CIBuildTrigger extends Trigger<AbstractProject<?, ?>> {
	private static final Logger log = Logger.getLogger(CIBuildTrigger.class.getName());

	private String selector;
	private String providerName;
	private List<MsgCheck> checks;

	public static final transient WeakHashMap<String, CITriggerThread> triggerInfo = new WeakHashMap<String, CITriggerThread>();
	private transient boolean providerUpdated;

	@DataBoundConstructor
	public CIBuildTrigger(String selector, String providerName, List<MsgCheck> checks) {
		super();
		this.selector = StringUtils.stripToNull(selector);
        this.providerName = providerName;
		if (checks == null) {
			checks = new ArrayList<MsgCheck>();
		}
		this.checks = checks;
	}

	@DataBoundSetter
	public void setProviderName(String providerName) {
		this.providerName = providerName;
	}

	@Override
	public void start(AbstractProject<?, ?> project, boolean newInstance) {
		super.start(project, newInstance);
		startTriggerThread();
	}

	public List<MsgCheck> getChecks() {
		return Collections.unmodifiableList(checks);
	}

	public static CIBuildTrigger findTrigger(String fullname) {
		Jenkins jenkins = Jenkins.getInstance();
		AbstractProject<?, ?> p = jenkins.getItemByFullName(fullname, AbstractProject.class);
		if (p != null) {
			return p.getTrigger(CIBuildTrigger.class);
		}
		return null;
	}

	@Override
	protected Object readResolve() throws ObjectStreamException {
		if (providerName == null && GlobalCIConfiguration.get().isMigrationInProgress()) {
			log.info("Provider is null and migration is in progress for providers...");
			JMSMessagingProvider provider = GlobalCIConfiguration.get()
					.getConfigs().get(0);
			if (provider != null) {
				providerName = provider.getName();
				providerUpdated = true;
				try {
					if (job != null) {
						job.save();
					}
				} catch (IOException e) {
					log.warning("Exception while trying to save job: " + e.getMessage());
				}
			}
		}
		return this;
	}

    @Override
	public void stop() {
		super.stop();
		stopTriggerThread();
	}

    public void rename(String oldFullName) {
        stopTriggerThread(oldFullName);
        startTriggerThread();
    }

	private void startTriggerThread() {
		if (providerUpdated) {
			log.info("Saving job since messaging provider was migrated...");
			try {
				job.save();
			} catch (IOException e) {
				log.warning("Exception while trying to save job: " + e.getMessage());
			}
		}
		if (job.isDisabled()) {
            log.info("Job '" + job.getFullName() + "' is disabled, not subscribing.");
        } else {
            try {
                stopTriggerThread();
	            JMSMessagingProvider provider = GlobalCIConfiguration.get()
			            .getProvider(providerName);
	            if (provider == null) {
					log.log(Level.SEVERE, "Failed to locate JMSMessagingProvider with name "
							+ providerName + ". You must update the job configuration. Trigger not started.");
					return;
				}
                CITriggerThread trigger = new CITriggerThread(provider, job
                        .getFullName(), selector, getChecks());
                trigger.setName("CIBuildTrigger-" + job.getFullName() + "-" + provider.getClass().getSimpleName());
	            trigger.setDaemon(true);
                trigger.start();
                triggerInfo.put(job.getFullName(), trigger);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in trigger start.", e);
            }
        }
	}

    private void stopTriggerThread() {
        stopTriggerThread(job.getFullName());
    }

    private void stopTriggerThread(String fullName) {
        CITriggerThread thread = triggerInfo.get(fullName);
        if (thread != null) {
            try {
                thread.sendInterrupt();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in trigger stop.", e);
            }
        }
        triggerInfo.remove(fullName);
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

    public String getProviderName() {
        return providerName;
    }

    @Extension
	public static class CIBuildTriggerDescriptor extends TriggerDescriptor {

        public ListBoxModel doFillProviderNameItems() {
            ListBoxModel items = new ListBoxModel();
            for (JMSMessagingProvider provider: GlobalCIConfiguration.get().getConfigs()) {
                items.add(provider.getName());
            }
            return items;
        }

		public FormValidation doCheckField(@QueryParameter String value) {
			String field = Util.fixEmptyAndTrim(value);
			if (field == null) {
				return FormValidation.error("Field cannot be empty");
			}
			return FormValidation.ok();
		}

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
