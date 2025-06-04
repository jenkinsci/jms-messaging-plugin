package com.redhat.jenkins.plugins.ci.messaging.topics;

import java.util.UUID;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public class UMBTopicProvider extends TopicProvider {

    private static final long serialVersionUID = -8194184157003849026L;

    @DataBoundConstructor
    public UMBTopicProvider() {
    }

    @Override
    public Descriptor<TopicProvider> getDescriptor() {
        return Jenkins.get().getDescriptorByType(UMBTopicProviderDescriptor.class);
    }

    @Extension
    public static class UMBTopicProviderDescriptor extends TopicProviderDescriptor {

        @Override
        public @Nonnull String getDisplayName() {
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
