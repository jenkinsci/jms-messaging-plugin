package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by shebert on 25/11/16.
 */
public class FerdmsgMessagingProvider extends MessagingProvider {

    @DataBoundConstructor
    public FerdmsgMessagingProvider(String name) {
        this.name = name;
    }

    @Override
    public Descriptor<MessagingProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(FedmsgMessagingProviderDescriptorImpl.class);
    }

    @Extension
    public static class FedmsgMessagingProviderDescriptorImpl extends MessageProviderDescriptorImpl {
        @Override
        public String getDisplayName() {
            return "fedmsg";
        }
    }

}
