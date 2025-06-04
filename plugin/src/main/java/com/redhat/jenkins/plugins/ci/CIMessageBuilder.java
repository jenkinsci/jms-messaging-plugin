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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.MessageUtils;
import com.redhat.utils.MessageUtils.MESSAGE_TYPE;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;

/**
 * Send message build step.
 */
public class CIMessageBuilder extends Builder {
    private static final Logger log = Logger.getLogger(CIMessageBuilder.class.getName());

    // All replaced by ProviderData
    @Deprecated
    private transient String providerName;
    @Deprecated
    private transient MessagingProviderOverrides overrides;
    @Deprecated
    private transient MESSAGE_TYPE messageType;
    @Deprecated
    private transient String messageProperties;
    @Deprecated
    private transient String messageContent;
    @Deprecated
    private transient boolean failOnError = false;

    private ProviderData providerData;

    @DataBoundConstructor
    public CIMessageBuilder() {
    }

    public CIMessageBuilder(ProviderData providerData) {
        super();
        this.providerData = providerData;
    }

    @Deprecated
    public String getProviderName() {
        return providerName;
    }

    @Deprecated
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    @Deprecated
    public MessagingProviderOverrides getOverrides() {
        return overrides;
    }

    @Deprecated
    public void setOverrides(MessagingProviderOverrides overrides) {
        this.overrides = overrides;
    }

    @Deprecated
    public MESSAGE_TYPE getMessageType() {
        return messageType;
    }

    @Deprecated
    public void setMessageType(MESSAGE_TYPE messageType) {
        this.messageType = messageType;
    }

    @Deprecated
    public String getMessageProperties() {
        return messageProperties;
    }

    @Deprecated
    public void setMessageProperties(String messageProperties) {
        this.messageProperties = messageProperties;
    }

    @Deprecated
    public String getMessageContent() {
        return messageContent;
    }

    @Deprecated
    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    @Deprecated
    public boolean isFailOnError() {
        return failOnError;
    }

    @Deprecated
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public ProviderData getProviderData() {
        return providerData;
    }

    @DataBoundSetter
    public void setProviderData(ProviderData providerData) {
        this.providerData = providerData;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        return MessageUtils.sendMessage(build, listener, providerData).isSucceeded();
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> arg0) {
            return true;
        }

        @Override
        public CIMessageBuilder newInstance(StaplerRequest2 sr, JSONObject jo) {
            try {
                // The provider name is at the root of the JSON object with a key of "" (this
                // is because the select is not named in dropdownList.jelly). Move that into the
                // provider data structure and then continue on.
                jo.getJSONObject("providerData").put("name", jo.remove(""));
                return (CIMessageBuilder) super.newInstance(sr, jo);
            } catch (hudson.model.Descriptor.FormException e) {
                log.log(Level.SEVERE, "Unable to create new instance.", e);
            }
            return null;
        }

        @Override
        public @Nonnull String getDisplayName() {
            return "CI Notifier";
        }
    }
}
