package com.redhat.jenkins.plugins.ci.pipeline;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;

import javax.inject.Inject;

import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.redhat.jenkins.plugins.ci.CIMessageSubscriberBuilder;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;

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
public class CIMessageSubscriberStep extends AbstractStepImpl {

    private String providerName;
    private MessagingProviderOverrides overrides;
    private String selector;
    private Integer timeout;

    @DataBoundConstructor
    public CIMessageSubscriberStep(final String providerName,
                                   final MessagingProviderOverrides overrides,
                                   final String selector,
                                   final Integer timeout) {
        super();
        this.providerName = providerName;
        this.overrides = overrides;
        this.selector = selector;
        this.timeout = timeout;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public MessagingProviderOverrides getOverrides() {
        return overrides;
    }

    public void setOverrides(MessagingProviderOverrides overrides) {
        this.overrides = overrides;
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
                    step.getOverrides(),
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

        public ListBoxModel doFillProviderNameItems() {
            ListBoxModel items = new ListBoxModel();
            for (JMSMessagingProvider provider: GlobalCIConfiguration.get().getConfigs()) {
                items.add(provider.getName());
            }
            return items;
        }

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
