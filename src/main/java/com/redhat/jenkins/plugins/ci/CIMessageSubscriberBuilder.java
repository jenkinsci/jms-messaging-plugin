package com.redhat.jenkins.plugins.ci;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;

/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */public class CIMessageSubscriberBuilder extends Builder {
    private static final Logger log = Logger.getLogger(CIMessageSubscriberBuilder.class.getName());

    private static final String BUILDER_NAME = Messages.SubscriberBuilder();

    public static final Integer DEFAULT_TIMEOUT_IN_MINUTES = 60;

    private String providerName;
    private MessagingProviderOverrides overrides;
    private String selector;
    private String variable;
    private List<MsgCheck> checks = new ArrayList<MsgCheck>();
    private Integer timeout = DEFAULT_TIMEOUT_IN_MINUTES;

    public CIMessageSubscriberBuilder(String providerName, MessagingProviderOverrides overrides,
                                      String selector, List<MsgCheck> checks, Integer timeout) {
        this(providerName, overrides, selector, null, checks, timeout);
    }

    @DataBoundConstructor
    public CIMessageSubscriberBuilder(String providerName,
                                      MessagingProviderOverrides overrides,
                                      String selector,
                                      String variable,
                                      List<MsgCheck> checks,
                                      Integer timeout
                                      ) {
        this.providerName = providerName;
        this.overrides = overrides;
        this.selector = selector;
        this.variable = variable;
        if (checks == null) {
            checks = new ArrayList<>();
        }
        this.checks = checks;
        if (timeout == null) {
            timeout = DEFAULT_TIMEOUT_IN_MINUTES;
        }
        this.timeout = timeout;
    }

    @DataBoundSetter
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }

    public MessagingProviderOverrides getOverrides() {
        return overrides;
    }

    @DataBoundSetter
    public void setOverrides(MessagingProviderOverrides overrides) {
        this.overrides = overrides;
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
        JMSMessagingProvider provider = config.getProvider(providerName);
        if (provider == null) {
            listener.error("Failed to locate JMSMessagingProvider with name "
                    + providerName + ". You must update the job configuration.");
            return null;
        }

        JMSMessagingWorker worker =
                provider.createWorker(overrides, build.getParent().getName());
        return worker.waitForMessage(build, listener, selector, variable, checks, timeout);
    }

    public boolean doMessageSubscribe(Run<?,?> run, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        if (waitforCIMessage(run, launcher, listener) == null) {
            return false;
        }
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return doMessageSubscribe(build, launcher, listener);
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
            MessagingProviderOverrides mpo = null;
            if (!jo.getJSONObject("overrides").isNullObject()) {
                mpo = new MessagingProviderOverrides(jo.getJSONObject("overrides").getString("topic"));
            }
            int timeout = getDefaultTimeout();
            if (jo.getString("timeout") != null && !jo.getString("timeout").isEmpty()) {
                timeout = jo.getInt("timeout");
            }
            List<MsgCheck> checks = new ArrayList<>();
            return new CIMessageSubscriberBuilder(
                    jo.getString("providerName"),
                    mpo,
                    jo.getString("selector"),
                    jo.getString("variable"),
                    checks,
                    timeout);
        }

        public ListBoxModel doFillProviderNameItems() {
            ListBoxModel items = new ListBoxModel();
            for (JMSMessagingProvider provider: GlobalCIConfiguration.get().getConfigs()) {
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
