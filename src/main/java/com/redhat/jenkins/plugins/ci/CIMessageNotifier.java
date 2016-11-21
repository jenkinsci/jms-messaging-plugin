package com.redhat.jenkins.plugins.ci;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.redhat.utils.MessageUtils;
import com.redhat.utils.MessageUtils.MESSAGE_TYPE;
import com.redhat.utils.PluginUtils;

public class CIMessageNotifier extends Notifier {

    private static final String BUILDER_NAME = Messages.MessageNotifier();

    private MESSAGE_TYPE messageType;
    private String messageProperties;
    private String messageContent;

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

    @DataBoundConstructor
    public CIMessageNotifier(final MESSAGE_TYPE messageType, final String messageProperties, final String messageContent) {
        super();
        this.messageType = messageType;
        this.messageProperties = messageProperties;
        this.messageContent = messageContent;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return MessageUtils.sendMessage(build, listener, getMessageType(),
                PluginUtils.getSubstitutedValue(getMessageProperties(), build.getEnvironment(listener)),
                PluginUtils.getSubstitutedValue(getMessageContent(), build.getEnvironment(listener)));
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private MESSAGE_TYPE messageType;
        private String messageProperties;
        private String messageContent;

        public DescriptorImpl() {
            load();
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

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> arg0) {
            return true;
        }

        @Override
        public CIMessageNotifier newInstance(StaplerRequest sr, JSONObject jo) {
            return new CIMessageNotifier(MESSAGE_TYPE.fromString(jo.getString("messageType")), jo.getString("messageProperties"), jo.getString("messageContent"));
        }

        @Override
        public boolean configure(StaplerRequest sr, JSONObject formData) throws FormException {
            setMessageType(MESSAGE_TYPE.fromString(formData.optString("messageType")));
            setMessageProperties(formData.optString("messageProperties"));
            setMessageContent(formData.optString("messageContent"));
            try {
                new CIMessageNotifier(getMessageType(), getMessageProperties(), getMessageContent());
            } catch (Exception e) {
                throw new FormException("Failed to initialize notifier - check your global notifier configuration settings", e, "");
            }
            save();
            return super.configure(sr, formData);
        }

        @Override
        public String getDisplayName() {
            return BUILDER_NAME;
        }

        public ListBoxModel doFillMessageTypeItems(@QueryParameter String messageType) {
            MESSAGE_TYPE current = MESSAGE_TYPE.fromString(messageType);
            ListBoxModel items = new ListBoxModel();
            for (MESSAGE_TYPE t : MESSAGE_TYPE.values()) {
                items.add(new ListBoxModel.Option(t.toDisplayName(), t.name(), (t == current) || items.size() == 0));
            }
            return items;
        }
    }
}
