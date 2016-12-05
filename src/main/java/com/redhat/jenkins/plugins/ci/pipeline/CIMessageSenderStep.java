package com.redhat.jenkins.plugins.ci.pipeline;

import javax.inject.Inject;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProvider;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.redhat.utils.MessageUtils;
import com.redhat.utils.MessageUtils.MESSAGE_TYPE;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

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
public class CIMessageSenderStep extends AbstractStepImpl {

    private String providerName;
    private MESSAGE_TYPE messageType;
    private String messageProperties;
    private String messageContent;

    @DataBoundConstructor
    public CIMessageSenderStep(final String providerName,
                               final MESSAGE_TYPE messageType,
                               final String messageProperties,
                               final String messageContent) {
        super();
        this.providerName = providerName;
        this.messageType = messageType;
        this.messageProperties = messageProperties;
        this.messageContent = messageContent;
    }

    public MESSAGE_TYPE getMessageType() {
        return messageType;
    }
    public void setMessageType(MESSAGE_TYPE messageType) {
        this.messageType = messageType;
    }
    public String getMessageProperties() {
        return messageProperties;
    }
    public void setMessageProperties(String messageProperties) {
        this.messageProperties = messageProperties;
    }
    public String getMessageContent() {
        return messageContent;
    }
    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
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
    public static class Execution extends AbstractSynchronousStepExecution<Void> {

        @StepContextParameter
        private transient Run build;

        @StepContextParameter
        private transient TaskListener listener;

        @Inject
        private transient CIMessageSenderStep step;

        @Override
        protected Void run() throws Exception {
            if (step.getProviderName() == null) {
                throw new Exception("providerName not specified!");
            }

            MessageUtils.sendMessage(build,
                    listener,
                    step.getProviderName(),
                    step.getMessageType(),
                    step.getMessageProperties(),
                    step.getMessageContent());
            return null;
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
            return "sendCIMessage";
        }

        @Override
        public String getDisplayName() {
            return Messages.MessageNotifier();
        }

        public ListBoxModel doFillProviderNameItems() {
            ListBoxModel items = new ListBoxModel();
            for (MessagingProvider provider: GlobalCIConfiguration.get().getConfigs()) {
                items.add(provider.getName());
            }
            return items;
        }

        public ListBoxModel doFillMessageTypeItems(@QueryParameter String messageType) {
            ListBoxModel items = new ListBoxModel();
            for (MESSAGE_TYPE t : MESSAGE_TYPE.values()) {
                items.add(new ListBoxModel.Option(t.toDisplayName(), t.name(), false));
            }
            return items;
        }

    }

}
