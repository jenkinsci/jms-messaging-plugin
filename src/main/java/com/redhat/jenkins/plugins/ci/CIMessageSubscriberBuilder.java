package com.redhat.jenkins.plugins.ci;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.MessagingWorker;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.logging.Logger;

import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class CIMessageSubscriberBuilder extends Builder {
    private static final Logger log = Logger.getLogger(CIMessageSubscriberBuilder.class.getName());

    private static final String BUILDER_NAME = Messages.SubscriberBuilder();

    public static final Integer DEFAULT_TIMEOUT_IN_MINUTES = 60;

    private String providerName;
    private String selector;
    private String variable;
    private Integer timeout;

    @DataBoundConstructor
    public CIMessageSubscriberBuilder(String providerName,
                                      String selector,
                                      String variable,
                                      Integer timeout) {
        this.providerName = providerName;
        this.selector = selector;
        this.variable = variable;
        this.timeout = timeout;
    }

    public CIMessageSubscriberBuilder(String providerName,
                                      String selector, Integer timeout) {
        this.providerName = providerName;
        this.selector = selector;
        this.timeout = timeout;
    }

    @DataBoundSetter
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }

    @DataBoundSetter
    public void setVariable(String variable) {
        this.variable = variable;
    }

    @DataBoundSetter
    public void setSelector(String selector) {
        this.selector = selector;
    }

    @DataBoundSetter
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getSelector() {
        return selector;
    }

    public String getVariable() {
        return variable;
    }

    public Integer getTimeout() {
        return timeout;
    }


    public String waitforCIMessage(Run<?, ?> build, Launcher launcher, TaskListener listener) {
        GlobalCIConfiguration config = GlobalCIConfiguration.get();
        MessagingWorker worker =
                config.getProvider(getProviderName()).createWorker(build
                        .getParent().getName());
        return worker.waitForMessage(build, selector, variable, timeout);
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (waitforCIMessage(build, launcher, listener)== null) {
            return false;
        }
        return true;
    }

    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {

        public String getDisplayName() {
            return BUILDER_NAME;
        }

        public Integer getDefaultTimeout() {
            return DEFAULT_TIMEOUT_IN_MINUTES;
        }

        @Override
        public CIMessageSubscriberBuilder newInstance(StaplerRequest sr, JSONObject jo) {
            int timeout = getDefaultTimeout();
            if (jo.getString("timeout") != null && !jo.getString("timeout").isEmpty()) {
                timeout = jo.getInt("timeout");
            }
            return new CIMessageSubscriberBuilder(
                    jo.getString("providerName"),
                    jo.getString("selector"),
                    jo.getString("variable"),
                    timeout);
        }

        public ListBoxModel doFillProviderNameItems() {
            ListBoxModel items = new ListBoxModel();
            for (MessagingProvider provider: GlobalCIConfiguration.get().getConfigs()) {
                items.add(provider.getName());
            }
            return items;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest sr, JSONObject formData) throws FormException {
            save();
            return super.configure(sr, formData);
        }

        public FormValidation doCheckSelector(
                @QueryParameter String selector) {
            if (selector == null || selector.isEmpty()) {
                return FormValidation.error("Please enter a JMS selector.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVariable(
                @QueryParameter String variable) {
            if (variable == null || variable.isEmpty()) {
                return FormValidation.error("Please enter a variable name to hold the received message result.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTimeout(
                @QueryParameter String timeout) {
            try {
                if (timeout == null || timeout.isEmpty() || Integer.parseInt(timeout) <= 0) {
                    return FormValidation.error("Please enter a positive timeout value.");
                }
            } catch (NumberFormatException e) {
                return FormValidation.error("Please enter a valid timeout value.");
            }
            return FormValidation.ok();
        }
    }
}
