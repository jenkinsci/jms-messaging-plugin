package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.jms.JMSException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by shebert on 25/11/16.
 */
public abstract class MessagingProvider implements Describable<MessagingProvider>, Serializable {

    protected String name;
    private static final Logger log = Logger.getLogger(MessagingProvider.class.getName());
    public final static String DEFAULT_PROVIDERNAME = "default";

    public String getName() {
        return name;
    }

    public abstract MessagingWorker createWorker(String jobname);

    public static boolean isValidURL(String url) {
        try {
            new URI(url);
        } catch (URISyntaxException e) {
            log.log(Level.SEVERE, "URISyntaxException, returning false.");
            return false;
        }
        return true;
    }

    public abstract static class MessagingProviderDescriptor extends Descriptor<MessagingProvider> {

        public static ExtensionList<MessagingProviderDescriptor> all() {
            return Jenkins.getInstance().getExtensionList(MessagingProviderDescriptor.class);
        }
    }
}
