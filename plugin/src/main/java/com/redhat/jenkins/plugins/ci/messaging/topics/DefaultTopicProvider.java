package com.redhat.jenkins.plugins.ci.messaging.topics;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class DefaultTopicProvider extends TopicProvider {

    private static final long serialVersionUID = -8194184157003849025L;

    @DataBoundConstructor
    public DefaultTopicProvider() {
    }

    @Override
    public Descriptor<TopicProvider> getDescriptor() {
        return Jenkins.get().getDescriptorByType(DefaultTopicProviderDescriptor.class);
    }

    @Extension
    public static class DefaultTopicProviderDescriptor extends TopicProviderDescriptor {

        @Override
        public @Nonnull String getDisplayName() {
            return "Default Topic Provider";
        }

        public String generatePublisherTopic() {
            return "";
        }

        public String generateSubscriberTopic() {
            return "";
        }
    }
}
