package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;

import java.io.Serializable;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class MessagingProviderOverrides implements Describable<MessagingProviderOverrides>, Serializable {
    private static final long serialVersionUID = -8815444484948038651L;

    private String topic;

    @DataBoundConstructor
    public MessagingProviderOverrides(String topic) {
        this.setTopic(topic);
    }

    public String getTopic() {
        return topic;
    }

    @DataBoundSetter
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Override
    public Descriptor<MessagingProviderOverrides> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(MessagingProviderOverridesDescriptor.class);
    }

    @Extension
    public static class MessagingProviderOverridesDescriptor extends Descriptor<MessagingProviderOverrides> {

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
