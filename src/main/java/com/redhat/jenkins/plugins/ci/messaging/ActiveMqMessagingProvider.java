package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by shebert on 25/11/16.
 */
public class ActiveMqMessagingProvider extends MessagingProvider {

    @DataBoundConstructor
    public ActiveMqMessagingProvider(String name) {
        this.name = name;
    }

    @Override
    public Descriptor<MessagingProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(ActiveMqMessagingProviderDescriptorImpl.class);
    }

    @Extension
    public static class ActiveMqMessagingProviderDescriptorImpl extends MessageProviderDescriptorImpl {
        @Override
        public String getDisplayName() {
            return "Active MQ";
        }
    }

}
