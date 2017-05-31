package com.redhat.jenkins.plugins.ci.messaging.topics;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

public class DefaultTopicProvider extends TopicProvider {

    private static final long serialVersionUID = -8194184157003849025L;

    @DataBoundConstructor
    public DefaultTopicProvider() {}

    @Override
    public Descriptor<TopicProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DefaultTopicProviderDescriptor.class);
    }

    @Extension
    public static class DefaultTopicProviderDescriptor extends TopicProviderDescriptor {

        @Override
        public String getDisplayName() {
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
