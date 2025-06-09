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
package com.redhat.jenkins.plugins.ci;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.threads.CITriggerThread;
import com.redhat.jenkins.plugins.ci.threads.CITriggerThreadFactory;
import com.redhat.jenkins.plugins.ci.threads.TriggerThreadProblemAction;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.BooleanParameterDefinition;
import hudson.model.BooleanParameterValue;
import hudson.model.CauseAction;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TextParameterDefinition;
import hudson.model.TextParameterValue;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;

public class CIBuildTrigger extends Trigger<Job<?, ?>> {
    public static final Logger RESOURCE_LOGGER = Logger.getLogger(CIBuildTrigger.class.getName());
    private static final Logger log = Logger.getLogger(CIBuildTrigger.class.getName());

    private Boolean noSquash;
    private List<ProviderData> providers;

    public static final ConcurrentMap<String, List<CITriggerThread>> locks = new ConcurrentHashMap<>();
    private transient boolean providerUpdated;

    private transient List<TriggerThreadProblemAction> actions = new ArrayList<>();

    @DataBoundConstructor
    public CIBuildTrigger() {
    }

    public CIBuildTrigger(Boolean noSquash, List<ProviderData> providers) {
        super();
        setNoSquash(noSquash);
        setProviders(providers);
    }

    public Boolean getNoSquash() {
        return noSquash;
    }

    @DataBoundSetter
    public void setNoSquash(Boolean noSquash) {
        this.noSquash = noSquash;
    }

    public List<? extends ProviderData> getProviders() {
        return providers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CIBuildTrigger that = (CIBuildTrigger) o;
        return Objects.equals(noSquash, that.noSquash) && Objects.equals(providers, that.providers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noSquash, providers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{providers: " + providers + ", noSquash: " + noSquash + "}";
    }

    @DataBoundSetter
    public void setProviders(List<ProviderData> providers) {
        this.providers = providers;
    }

    public static CIBuildTrigger findTrigger(String fullname) {
        Jenkins jenkins = Jenkins.get();

        final Job<?, ?> p = jenkins.getItemByFullName(fullname, Job.class);
        if (p != null) {
            CIBuildTrigger cibt = ParameterizedJobMixIn.getTrigger(p, CIBuildTrigger.class);
            if (cibt != null) {
                log.warning("Trigger for job '" + fullname + "' is: " + cibt.toString());
            } else {
                log.warning("Trigger for job '" + fullname + "' is: NULL");
            }
            return cibt;
            // return ParameterizedJobMixIn.getTrigger(p, CIBuildTrigger.class);
        }
        log.warning("Unable to find job '" + fullname + "'");
        return null;
    }

    @Override
    protected Object readResolve() {
        return this;
    }

    @Override
    public void start(Job project, boolean newInstance) {
        super.start(project, newInstance);
        startTriggerThreads();
    }

    @Override
    public void stop() {
        super.stop();
        if (job != null) {
            stopTriggerThreads(job.getFullName());
        } else {
            log.fine("job is null! Not stopping trigger thread!");
        }
    }

    public void force(String fullName) {
        stopTriggerThreads(fullName, null);
    }

    public void rename(String oldFullName) {
        stopTriggerThreads(oldFullName);
        startTriggerThreads();
    }

    private void startTriggerThreads() {
        if (job == null)
            return;

        if (providerUpdated) {
            log.info("Saving job since messaging provider was migrated...");
            try {
                job.save();
            } catch (IOException e) {
                log.warning("Exception while trying to save job: " + e.getMessage());
            }
        }
        if (job instanceof AbstractProject) {
            AbstractProject<?, ?> aJob = (AbstractProject<?, ?>) job;
            if (aJob.isDisabled()) {
                log.info("Job '" + job.getFullName() + "' is disabled, not subscribing.");
                return;
            }
        }
        try {
            synchronized (locks.computeIfAbsent(job.getFullName(), o -> new ArrayList<>())) {
                if (job != null && stopTriggerThreads(job.getFullName()) == null && providers != null) {
                    List<CITriggerThread> threads = locks.get(Objects.requireNonNull(job).getFullName());
                    int instance = 1;
                    for (ProviderData pd : providers) {
                        JMSMessagingProvider provider = GlobalCIConfiguration.get().getProvider(pd.getName());
                        if (provider == null) {
                            log.log(Level.SEVERE, "Failed to locate JMSMessagingProvider with name " + pd.getName()
                                    + ". You must update the job configuration. Trigger not started.");
                            return;
                        }
                        CITriggerThread thread = CITriggerThreadFactory.createCITriggerThread(provider, pd,
                                job.getFullName(), this, instance);
                        log.info("Starting thread (" + thread.getId() + ") for '"
                                + Objects.requireNonNull(job).getFullName() + "'.");
                        thread.start();
                        threads.add(thread);
                        instance++;
                    }
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception in trigger start.", e);
        }
    }

    private List<CITriggerThread> stopTriggerThreads(String fullName) {
        return stopTriggerThreads(fullName, null);
        // return stopTriggerThreads(fullName, getComparisonThreads());
    }

    private List<CITriggerThread> stopTriggerThreads(String fullName, List<CITriggerThread> comparisonThreads) {
        synchronized (locks.computeIfAbsent(fullName, o -> new ArrayList<>())) {
            List<CITriggerThread> threads = locks.get(fullName);
            // If threads are the same we have start/stop sequence, so don't bother stopping.
            if (comparisonThreads != null && threads.size() == comparisonThreads.size()) {
                for (CITriggerThread thread : threads) {
                    ListIterator<CITriggerThread> iter = comparisonThreads.listIterator();
                    while (iter.hasNext()) {
                        Thread t = iter.next();
                        if (thread.equals(t)) {
                            log.info("Already have thread " + thread.getId() + "...");
                            iter.remove();
                            break;
                        }
                    }
                }
                if (comparisonThreads.size() == 0) {
                    return threads;
                }
            }

            for (CITriggerThread thread : threads) {
                try {
                    log.info("Stopping thread (" + thread.getId() + ") for '" + fullName + "'.");
                    thread.shutdown();
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Unhandled exception in trigger stop.", e);
                }
            }

            // Just in case.
            threads.clear();
            log.fine("Removed thread lock for '" + fullName + "'.");
        }
        return null;
    }

    private List<CITriggerThread> getComparisonThreads() {
        if (providers != null && job != null) {
            List<CITriggerThread> threads = new ArrayList<>();
            int instance = 1;
            for (ProviderData pd : providers) {
                JMSMessagingProvider provider = GlobalCIConfiguration.get().getProvider(pd.getName());
                // We create a new thread here only to be able to
                // use .equals() to compare.
                // The thread is never started.
                if (provider == null)
                    throw new NullPointerException("No such provider configured for name: '" + pd.getName()
                            + "' on job named '" + job.getFullName() + "'");
                CITriggerThread thread = new CITriggerThread(provider, pd, job.getFullName(), null, instance);
                threads.add(thread);
                instance++;
            }
            return threads;
        }
        return null;
    }

    public void addJobAction(Exception e) {
        getJobActions().add(new TriggerThreadProblemAction(e));
    }

    public List<TriggerThreadProblemAction> getJobActions() {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        return actions;
    }

    public void clearJobActions() {
        getJobActions().clear();
    }

    /**
     * Inspects {@link ParametersAction} to see what kind of capabilities it has in regards to SECURITY-170. Assuming
     * the safeParameters constructor could not be found.
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
     *
     * @see #getParametersInspection()
     */
    private static volatile ParametersActionInspection parametersInspectionCache = null;

    /**
     * Data structure with information regarding what kind of capabilities {@link ParametersAction} has.
     */
    private static class ParametersActionInspection {
        private static final Class<ParametersAction> KLASS = ParametersAction.class;
        private boolean inspectionFailure;
        private boolean keepUndefinedParameters = false;
        private boolean hasSafeParameterConfig = false;

        /**
         * Constructor that performs the inspection.
         */
        ParametersActionInspection() {
            try {
                for (Field field : KLASS.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            && (field.getName().equals("KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME")
                                    || field.getName().equals("SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME"))) {
                        this.hasSafeParameterConfig = true;
                        break;
                    }
                }
                if (hasSafeParameterConfig) {
                    if (Boolean.getBoolean(KLASS.getName() + ".keepUndefinedParameters")) {
                        this.keepUndefinedParameters = true;
                    }
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

    protected ParametersAction createParameters(Job<?, ?> project, Map<String, String> messageParams) {
        List<ParameterValue> definedParameters = getDefinedParameters(project);
        List<ParameterValue> parameters = getUpdatedParameters(messageParams, definedParameters);
        try {
            Constructor<ParametersAction> constructor = ParametersAction.class.getConstructor(List.class);
            return constructor.newInstance(parameters);
        } catch (NoSuchMethodException e) {
            ParametersActionInspection inspection = getParametersInspection();
            if (inspection.isInspectionFailure()) {
                log.log(Level.WARNING,
                        "Failed to inspect ParametersAction to determine "
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
            log.log(Level.WARNING,
                    "Running on a core with safe parameters fix available, but not allowed to specify them");
        } catch (Exception e) {
            log.log(Level.WARNING, "Running on a core with safe parameters fix available, but failed to provide them");
        }
        return new ParametersAction(parameters);
    }

    public void scheduleBuild(Map<String, String> messageParams) {
        if (job == null) {
            throw new IllegalStateException("Trigger not started yet");
        }
        ParametersAction parameters = createParameters(job, messageParams);
        List<ParameterValue> definedParameters = getDefinedParameters(Objects.requireNonNull(job));
        List<ParameterValue> buildParameters = getUpdatedParameters(messageParams, definedParameters);
        ParameterizedJobMixIn<?, ?> jobMixIn = new ParameterizedJobMixIn() {
            @Override
            protected Job<?, ?> asJob() {
                return job;
            }
        };

        jobMixIn.scheduleBuild2(0, new CauseAction(new CIBuildCause()), parameters,
                new CIEnvironmentContributingAction(messageParams, buildParameters),
                new CIShouldScheduleQueueAction(noSquash));
    }

    private List<ParameterValue> getUpdatedParameters(Map<String, String> messageParams,
            List<ParameterValue> definedParams) {
        // Update any build parameters that may have values from the triggering message.
        HashMap<String, ParameterValue> newParams = new HashMap<>();
        for (ParameterValue def : definedParams) {
            newParams.put(def.getName(), def);
        }
        for (Map.Entry<String, String> e : messageParams.entrySet()) {
            String key = e.getKey();

            if (newParams.containsKey(key)) {
                if (newParams.get(key) instanceof TextParameterValue) {
                    TextParameterValue tpv = new TextParameterValue(key, messageParams.get(key));
                    newParams.put(key, tpv);
                } else if (newParams.get(key) instanceof BooleanParameterValue) {
                    BooleanParameterValue bpv = new BooleanParameterValue(key,
                            Boolean.parseBoolean(messageParams.get(key)));
                    newParams.put(key, bpv);
                } else {
                    StringParameterValue spv = new StringParameterValue(key, messageParams.get(key));
                    newParams.put(key, spv);
                }
            }
        }
        return new ArrayList<>(newParams.values());
    }

    private List<ParameterValue> getDefinedParameters(Job<?, ?> project) {
        List<ParameterValue> parameters = new ArrayList<>();
        ParametersDefinitionProperty properties = project.getProperty(ParametersDefinitionProperty.class);

        if (properties != null && properties.getParameterDefinitions() != null) {
            for (ParameterDefinition paramDef : properties.getParameterDefinitions()) {
                ParameterValue param = null;
                if (paramDef instanceof StringParameterDefinition) {
                    param = new StringParameterValue(paramDef.getName(),
                            ((StringParameterDefinition) paramDef).getDefaultValue());
                } else if (paramDef instanceof TextParameterDefinition) {
                    param = new TextParameterValue(paramDef.getName(),
                            ((TextParameterDefinition) paramDef).getDefaultValue());
                } else if (paramDef instanceof BooleanParameterDefinition) {
                    BooleanParameterValue defaultParameterValue = ((BooleanParameterDefinition) paramDef)
                            .getDefaultParameterValue();
                    if (defaultParameterValue != null) {
                        param = new BooleanParameterValue(paramDef.getName(),
                                Boolean.TRUE.equals(Objects.requireNonNull(defaultParameterValue).getValue()));
                    }
                } else if (paramDef instanceof ChoiceParameterDefinition) {
                    param = ((ChoiceParameterDefinition) paramDef).getDefaultParameterValue();
                }

                if (param != null) {
                    parameters.add(param);
                }
            }
        }
        return parameters;
    }

    private void saveJob() {
        try {
            if (job != null) {
                job.save();
            }
        } catch (IOException e) {
            log.warning("Exception while trying to save job: " + e.getMessage());
        }
    }

    private String getJobName() {
        return (job != null) ? job.getName() : "<unknown>";
    }

    @Override
    public CIBuildTriggerDescriptor getDescriptor() {
        return (CIBuildTriggerDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Extension
    @Symbol("ciBuildTrigger")
    public static class CIBuildTriggerDescriptor extends TriggerDescriptor {

        public FormValidation doCheckField(@QueryParameter String value) {
            String field = Util.fixEmptyAndTrim(value);
            if (field == null) {
                return FormValidation.error("Field cannot be empty");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public @Nonnull String getDisplayName() {
            return Messages.PluginName();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/jms-messaging/help-trigger.html";
        }
    }
}
