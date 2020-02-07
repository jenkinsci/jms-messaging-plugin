package com.redhat.jenkins.plugins.ci.messaging.topics;

import hudson.Extension;
import hudson.model.Descriptor;

import java.util.UUID;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

public class UMBTopicProvider extends TopicProvider {

    private static final long serialVersionUID = -8194184157003849026L;

    @DataBoundConstructor
    public UMBTopicProvider() {}

    @Override
    public Descriptor<TopicProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(UMBTopicProviderDescriptor.class);
    }

    @Extension
    public static class UMBTopicProviderDescriptor extends TopicProviderDescriptor {

        @Override
        public String getDisplayName() {
            return "Red Hat UMB Topic Provider";
        }

        public String generatePublisherTopic() {
            return "VirtualTopic.qe.ci.jenkins";
         }

        public String generateSubscriberTopic() {
            return "Consumer.rh-jenkins-ci-plugin." + UUID.randomUUID() + ".VirtualTopic.qe.ci.>";
         }
    }
}
