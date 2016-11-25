package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * Created by shebert on 25/11/16.
 */
public abstract class MessagingProvider implements Describable<MessagingProvider>, Serializable {

    String name;

    public String getName() {
        return name;
    }

    public abstract static class MessageProviderDescriptorImpl extends Descriptor<MessagingProvider> {

        public static ExtensionList<MessageProviderDescriptorImpl> all() {
            return Jenkins.getInstance().getExtensionList(MessageProviderDescriptorImpl.class);
        }
    }
}
