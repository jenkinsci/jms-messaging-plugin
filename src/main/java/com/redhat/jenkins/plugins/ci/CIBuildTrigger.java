package com.redhat.jenkins.plugins.ci;

import hudson.Extension;
import hudson.Util;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;

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
public class CIBuildTrigger extends Trigger<BuildableItem> {
	private static final Logger log = Logger.getLogger(CIBuildTrigger.class.getName());

	private String selector;
	private String providerName;
	private List<MsgCheck> checks = new ArrayList<MsgCheck>();
	private MessagingProviderOverrides overrides;

	public static final transient WeakHashMap<String, CITriggerThread> triggerInfo = new WeakHashMap<String, CITriggerThread>();
	private transient boolean providerUpdated;

	@DataBoundConstructor
	public CIBuildTrigger(String selector, String providerName, MessagingProviderOverrides overrides, List<MsgCheck> checks) {
		super();
		this.selector = StringUtils.stripToNull(selector);
        this.providerName = providerName;
        this.overrides = overrides;
		if (checks == null) {
			checks = new ArrayList<MsgCheck>();
		}
		this.checks = checks;
	}

	@DataBoundSetter
	public void setProviderName(String providerName) {
		this.providerName = providerName;
	}

	public MessagingProviderOverrides getOverrides() {
        return overrides;
    }

    @DataBoundSetter
    public void setOverrides(MessagingProviderOverrides overrides) {
        this.overrides = overrides;
    }

	public List<MsgCheck> getChecks() {
		if (checks == null) {
			checks = new ArrayList<MsgCheck>();
		}
		return Collections.unmodifiableList(checks);
	}

	public static CIBuildTrigger findTrigger(String fullname) {
		Jenkins jenkins = Jenkins.getInstance();

		final Job p = jenkins.getItemByFullName(fullname, Job.class);
		if (p != null) {
			return ParameterizedJobMixIn.getTrigger(p, CIBuildTrigger.class);
		}
		return null;
	}

	@Override
	protected Object readResolve() throws ObjectStreamException {
		if (providerName == null) {
			log.info("Provider is null for trigger for job");
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
	public void start(BuildableItem project, boolean newInstance) {
		super.start(project, newInstance);
		startTriggerThread();
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
		if (job instanceof AbstractProject) {
			AbstractProject aJob = (AbstractProject) job;
			if (aJob.isDisabled()) {
				log.info("Job '" + job.getFullName() + "' is disabled, not subscribing.");
					return;
			}
		}
		try {
			if (stopTriggerThread() == null) {
				JMSMessagingProvider provider = GlobalCIConfiguration.get()
						.getProvider(providerName);
				if (provider == null) {
					log.log(Level.SEVERE, "Failed to locate JMSMessagingProvider with name "
							+ providerName + ". You must update the job configuration. Trigger not started.");
					return;
				}
				CITriggerThread trigger = new CITriggerThread(provider, overrides, job
						.getFullName(), selector, getChecks());
				trigger.setName("CIBuildTrigger-" + job.getFullName() + "-" + provider.getClass().getSimpleName());
				trigger.setDaemon(true);
				trigger.start();
				log.fine("Adding thread: " + trigger.getId());
				triggerInfo.put(job.getFullName(), trigger);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, "Unhandled exception in trigger start.", e);
		}
	}

    private CITriggerThread stopTriggerThread() {
        if (job != null) {
            return stopTriggerThread(job.getFullName());
        }
        return null;
    }

    private CITriggerThread stopTriggerThread(String fullName) {
        CITriggerThread thread = triggerInfo.get(fullName);
        if (thread != null) {

			JMSMessagingProvider provider = GlobalCIConfiguration.get()
					.getProvider(providerName);
			CITriggerThread newThread = new CITriggerThread(provider, overrides, job
					.getFullName(), selector, getChecks());
			if (thread.equals(newThread)) {
				log.fine("Already have thread " + thread.getId() + "...");
				return thread;
			}

            log.fine("Getting thread: " + thread.getId());
            try {
                int waitCount = 0;
                while (waitCount <= 60 && !thread.isMessageProviderConnected()) {
                    log.fine("Thread " + thread.getId() + ": Message Provider is NOT connected. Sleeping 1 sec");
                    Thread.sleep(1000);
                    waitCount++;
                }
                if (waitCount > 60) {
                    log.warning("Wait time of 60 secs elapsed trying to connect before interrupting...");
                }
                thread.sendInterrupt();
                thread.interrupt();
                if (thread.isMessageProviderConnected()) {
                    log.fine("Thread " + thread.getId() + ": Message Provider is connected");
                    log.fine("Thread " + thread.getId() + ": trying to join");
                    thread.join();
                } else {
                    log.warning("Thread " + thread.getId() + " Message Provider is NOT connected; skipping join!");
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in trigger stop.", e);
            }
        }
        CITriggerThread thread2 = triggerInfo.remove(fullName);
        if (thread2 != null) {
            log.fine("Removed thread: " + thread2.getId());
        }
        return null;
    }

	public String getSelector() {
		return selector;
	}

	public void setSelector(String selector) {
		this.selector = selector;
	}

    /**
     * Inspects {@link ParametersAction} to see what kind of capabilities it has in regards to SECURITY-170.
     * Assuming the safeParameters constructor could not be found.
     *
     * @return the inspection result
     */
    private static synchronized ParametersActionInspection getParametersInspection() {
        if (parametersInspectionCache == null) {
            parametersInspectionCache = new ParametersActionInspection();
        }
        return parametersInspectionCache;
    }

    /**
     * Stored cache of the inspection.
     * @see #getParametersInspection()
     */
    private static volatile ParametersActionInspection parametersInspectionCache = null;
    /**
     * Data structure with information regarding what kind of capabilities {@link ParametersAction} has.
     */
    private static class ParametersActionInspection {
        private static final Class<ParametersAction> KLASS = ParametersAction.class;
        private boolean inspectionFailure;
        private boolean safeParametersSet = false;
        private boolean keepUndefinedParameters = false;
        private boolean hasSafeParameterConfig = false;

        /**
         * Constructor that performs the inspection.
         */
        ParametersActionInspection() {
            try {
                for (Field field : KLASS.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            &&  (
                            field.getName().equals("KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME")
                                    || field.getName().equals("SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME")
                    )
                            ) {
                        this.hasSafeParameterConfig = true;
                        break;
                    }
                }
                if (hasSafeParameterConfig) {
                    if (Boolean.getBoolean(KLASS.getName() + ".keepUndefinedParameters")) {
                        this.keepUndefinedParameters = true;
                    }
                    this.safeParametersSet = false;
                }
                this.inspectionFailure = false;
            } catch (Exception e) {
                this.inspectionFailure = true;
            }
        }

        /**
         * If the system property .keepUndefinedParameters is set and set to true.
         *
         * @return true if so.
         */
        boolean isKeepUndefinedParameters() {
            return keepUndefinedParameters;
        }

        /**
         * If any of the constant fields regarding safeParameters are declared in {@link ParametersAction}.
         *
         * @return true if so.
         */
        boolean isHasSafeParameterConfig() {
            return hasSafeParameterConfig;
        }

        /**
         * If there was an exception when inspecting the class.
         *
         * @return true if so.
         */
        public boolean isInspectionFailure() {
            return inspectionFailure;
        }
    }

    protected ParametersAction createParameters(BuildableItem project, Map<String, String> messageParams) {
        List<ParameterValue> definedParameters = getDefinedParameters(project);
        List<ParameterValue> parameters = getUpdatedParameters(messageParams, definedParameters);
        try {
            Constructor<ParametersAction> constructor = ParametersAction.class.getConstructor(List.class);
            return constructor.newInstance(parameters);
        } catch (NoSuchMethodException e) {
            ParametersActionInspection inspection = getParametersInspection();
            if (inspection.isInspectionFailure()) {
                log.log(Level.WARNING, "Failed to inspect ParametersAction to determine "
                        + "if we can behave normally around SECURITY-170.\nSee "
                        + "https://wiki.jenkins-ci.org/display/SECURITY/Jenkins+Security+Advisory+2016-05-11"
                        + " for information.");
            } else if (inspection.isHasSafeParameterConfig()) {
                StringBuilder txt = new StringBuilder(
                        "Running on a core with SECURITY-170 fixed but no direct way for Gerrit Trigger"
                                + " to self-specify safe parameters.");
                txt.append(" You should consider upgrading to a new Jenkins core version.\n");
                if (inspection.isKeepUndefinedParameters()) {
                    txt.append(".keepUndefinedParameters is set so the trigger should behave normally.");
                } else {
                    txt.append("No overriding system properties appears to be set,");
                    txt.append(" your builds might not work as expected.\n");
                    txt.append("See https://wiki.jenkins-ci.org/display/SECURITY/Jenkins+Security+Advisory+2016-05-11");
                    txt.append(" for information.");
                }
                log.log(Level.WARNING, txt.toString());
            } else {
                log.log(Level.FINE, "Running on an old core before safe parameters, we should be safe.");
            }
        } catch (IllegalAccessException e) {
            log.log(Level.WARNING, "Running on a core with safe parameters fix available, but not allowed to specify them");
        } catch (Exception e) {
            log.log(Level.WARNING, "Running on a core with safe parameters fix available, but failed to provide them");
        }
        return new ParametersAction(parameters);
    }

    public void scheduleBuild(Map<String, String> messageParams) {
        ParametersAction parameters = createParameters(job, messageParams);
	    List<ParameterValue> definedParameters = getDefinedParameters(job);
	    List<ParameterValue> buildParameters = getUpdatedParameters(messageParams, definedParameters);
        ParameterizedJobMixIn jobMixIn = new ParameterizedJobMixIn() {
            @Override
            protected Job asJob() {
                return (Job)job;
            }
        };

        jobMixIn.scheduleBuild2(0,
                new CauseAction(new CIBuildCause()),
                parameters,
                new CIEnvironmentContributingAction(messageParams, buildParameters)
                );
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

	private List<ParameterValue> getDefinedParameters(BuildableItem project) {
	    List<ParameterValue> parameters = new ArrayList<ParameterValue>();
	    ParametersDefinitionProperty properties = (ParametersDefinitionProperty)
                ((Job)project).getProperty(ParametersDefinitionProperty.class);

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
