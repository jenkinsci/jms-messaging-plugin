package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;

import java.io.Serializable;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class MessagingProviderOverrides implements Describable<MessagingProviderOverrides>, Serializable {
    private static final long serialVersionUID = -8815444484948038651L;

    private String topic;
    private String queue;

    @Whitelisted
    @DataBoundConstructor
    public MessagingProviderOverrides(String topic) {
        this.setTopic(topic);
    }

    public String getTopic() {
        return topic;
    }


    public String getQueue() {
        return queue;
    }

    @DataBoundSetter
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @DataBoundSetter
    public void setQueue(String queue) {
        this.queue = queue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessagingProviderOverrides that = (MessagingProviderOverrides) o;

        return (topic != null ? topic.equals(that.topic) : that.topic == null) &&
                (queue != null ? queue.equals(that.queue) : that.queue == null);
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
