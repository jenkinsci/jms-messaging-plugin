package com.redhat.jenkins.plugins.ci.pipeline;

import javax.inject.Inject;

import com.redhat.jenkins.plugins.ci.Messages;
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

public class CIMessageSenderStep extends AbstractStepImpl {

    private MESSAGE_TYPE messageType;
    private String messageProperties;
    private String messageContent;

    @DataBoundConstructor
    public CIMessageSenderStep(final MESSAGE_TYPE messageType, final String messageProperties, final String messageContent) {
        super();
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
            MessageUtils.sendMessage(build,
                    listener,
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

        public ListBoxModel doFillMessageTypeItems(@QueryParameter String messageType) {
            ListBoxModel items = new ListBoxModel();
            for (MESSAGE_TYPE t : MESSAGE_TYPE.values()) {
                items.add(new ListBoxModel.Option(t.toDisplayName(), t.name(), false));
            }
            return items;
        }

    }

}
