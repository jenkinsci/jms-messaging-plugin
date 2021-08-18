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
 */
package com.redhat.jenkins.plugins.ci;

import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingWorker;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Await message build step.
 */
public class CIMessageSubscriberBuilder extends Builder {
    private static final Logger log = Logger.getLogger(CIMessageSubscriberBuilder.class.getName());

    private static final String BUILDER_NAME = Messages.SubscriberBuilder();

    @Deprecated private transient String providerName;
    @Deprecated private transient MessagingProviderOverrides overrides;
    @Deprecated private transient String selector;
    @Deprecated private transient String variable;
    @Deprecated private transient List<MsgCheck> checks = new ArrayList<>();
    @Deprecated private transient Integer timeout;
    private ProviderData providerData;

    @DataBoundConstructor
    public CIMessageSubscriberBuilder() {
    }

    public CIMessageSubscriberBuilder(ProviderData providerData) {
        super();
        this.providerData = providerData;
    }

    @Deprecated public String getProviderName() {
        return providerName;
    }

    @Deprecated public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    @Deprecated public MessagingProviderOverrides getOverrides() {
        return overrides;
    }

    @Deprecated public void setOverrides(MessagingProviderOverrides overrides) {
        this.overrides = overrides;
    }

    @Deprecated public String getSelector() {
        return selector;
    }

    @Deprecated public void setSelector(String selector) {
        this.selector = selector;
    }

    @Deprecated public String getVariable() {
        return variable;
    }

    @Deprecated public void setVariable(String variable) {
        this.variable = variable;
    }

    @Deprecated public List<MsgCheck> getChecks() {
        return checks;
    }

    @Deprecated public void setChecks(List<MsgCheck> checks) {
        this.checks = checks;
    }

    @Deprecated public Integer getTimeout() {
        return timeout;
    }

    @Deprecated public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public ProviderData getProviderData() {
        return providerData;
    }

    @DataBoundSetter
    public void setProviderData(ProviderData providerData) {
        this.providerData = providerData;
    }

    public JMSMessagingProvider getProvider() {
        return GlobalCIConfiguration.get().getProvider(providerData.getName());
    }

    public String waitforCIMessage(Run<?, ?> build, Launcher launcher, TaskListener listener) {
        JMSMessagingProvider provider = GlobalCIConfiguration.get().getProvider(providerData.getName());
        if (provider == null) {
            listener.error("Failed to locate JMSMessagingProvider with name "
                    + providerData.getName() + ". You must update the job configuration.");
            return null;
        }

        JMSMessagingWorker worker = provider.createWorker(providerData, build.getParent().getName());
        return worker.waitForMessage(build, listener, providerData);
    }

    public boolean doMessageSubscribe(Run<?, ?> run, Launcher launcher, TaskListener listener) {
        return waitforCIMessage(run, launcher, listener) != null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        return doMessageSubscribe(build, launcher, listener);
    }

    @Extension
    public static class CIMessageSubscriberBuilderDescriptor extends BuildStepDescriptor<Builder> {

        public @Nonnull String getDisplayName() {
            return BUILDER_NAME;
        }

        @Override
        public CIMessageSubscriberBuilder newInstance(StaplerRequest sr, JSONObject jo) {
            try {
                // The provider name is at the root of the JSON object with a key of "" (this
                // is because the select is not named in dropdownList.jelly). Move that into the
                // provider data structure and then continue on.
                jo.getJSONObject("providerData").put("name", jo.remove(""));
                return (CIMessageSubscriberBuilder) super.newInstance(sr, jo);
            } catch (hudson.model.Descriptor.FormException e) {
                log.log(Level.SEVERE, "Unable to create new instance.", e);
            }
            return null;
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
    }
}
