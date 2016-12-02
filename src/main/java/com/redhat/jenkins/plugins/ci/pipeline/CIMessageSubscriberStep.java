package com.redhat.jenkins.plugins.ci.pipeline;

import com.redhat.jenkins.plugins.ci.CIMessageSubscriberBuilder;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.utils.MessageUtils;
import com.redhat.utils.MessageUtils.MESSAGE_TYPE;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.inject.Inject;

public class CIMessageSubscriberStep extends AbstractStepImpl {

    private String providerName;
    private String selector;
    private Integer timeout;

    @DataBoundConstructor
    public CIMessageSubscriberStep(final String providerName,
                                   final String selector,
                                   final Integer timeout) {
        super();
        this.providerName = providerName;
        this.selector = selector;
        this.timeout = timeout;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    /**
     * Executes the sendCIMessage step.
     */
    public static class Execution extends AbstractSynchronousStepExecution<String> {

        @StepContextParameter
        private transient Run build;

        @StepContextParameter
        private transient TaskListener listener;

        @Inject
        private transient CIMessageSubscriberStep step;

        @StepContextParameter
        private transient Launcher launcher;

        @Override
        protected String run() throws Exception {
            if (step.getProviderName() == null) {
                throw new Exception("providerName not specified!");
            }
            int timeout = CIMessageSubscriberBuilder.DEFAULT_TIMEOUT_IN_MINUTES;
            if (step.getTimeout() != null && step.getTimeout() > 0) {
                timeout = step.getTimeout();
            }
            CIMessageSubscriberBuilder builder = new CIMessageSubscriberBuilder(step.getProviderName(),
                    step.getSelector(),
                    timeout);
            return builder.waitforCIMessage(build, launcher, listener);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Adds the step as a workflow extension.
     */
    @Extension(optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        /**
         * Constructor.
         */
        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "waitForCIMessage";
        }

        @Override
        public String getDisplayName() {
            return Messages.SubscriberBuilder();
        }

    }
}
