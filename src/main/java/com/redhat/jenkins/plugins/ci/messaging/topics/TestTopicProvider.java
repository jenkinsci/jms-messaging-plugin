package com.redhat.jenkins.plugins.ci.messaging.topics;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

public class TestTopicProvider extends TopicProvider {

    private static final long serialVersionUID = -8194184157003849026L;

    @DataBoundConstructor
    public TestTopicProvider() {}

    @Override
    public Descriptor<TopicProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(TestTopicProviderDescriptor.class);
    }

    @Extension
    public static class TestTopicProviderDescriptor extends TopicProviderDescriptor {

        @Override
        public String getDisplayName() {
            return "Test Topic Provider";
        }

        public String generatePublisherTopic() {
            return "test-publisher";
         }

        public String generateSubscriberTopic() {
            return "test-subscriber";
         }
    }
}
