package com.redhat.jenkins.plugins.ci;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProvider;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.redhat.utils.MessageUtils;
import com.redhat.utils.MessageUtils.MESSAGE_TYPE;

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
public class CIMessageBuilder extends Builder {

    private String providerName;
    private MESSAGE_TYPE messageType;
    private String messageProperties;
    private String messageContent;

    public MESSAGE_TYPE getMessageType() {
        return messageType;
    }
    public void setMessageType(MESSAGE_TYPE messageType) {
        this.messageType = messageType;
    }
    public String getMessageProperties() {
        return messageProperties;
    }
    public void setMessageProperties(String messageProperties) {
        this.messageProperties = messageProperties;
    }
    public String getMessageContent() {
        return messageContent;
    }
    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    @DataBoundConstructor
    public CIMessageBuilder(final String providerName,
                            final MESSAGE_TYPE messageType,
                            final String messageProperties,
                            final String messageContent) {
        super();
        this.providerName = providerName;
        this.messageType = messageType;
        this.messageProperties = messageProperties;
        this.messageContent = messageContent;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return MessageUtils.sendMessage(build, listener,
                getProviderName(),
                getMessageType(),
                getMessageProperties(),
                getMessageContent());
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private String providerName;
        private MESSAGE_TYPE messageType;
        private String messageProperties;
        private String messageContent;

        public DescriptorImpl() {
            load();
        }

        public MESSAGE_TYPE getMessageType() {
            return messageType;
        }

        public void setMessageType(MESSAGE_TYPE messageType) {
            this.messageType = messageType;
        }

        public String getMessageProperties() {
            return messageProperties;
        }

        public void setMessageProperties(String messageProperties) {
            this.messageProperties = messageProperties;
        }

        public String getMessageContent() {
            return messageContent;
        }

        public void setMessageContent(String messageContent) {
            this.messageContent = messageContent;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> arg0) {
            return true;
        }


        @Override
        public CIMessageBuilder newInstance(StaplerRequest sr, JSONObject jo) {
            return new CIMessageBuilder(jo.getString("providerName"),
                    MESSAGE_TYPE.fromString(jo.getString("messageType")),
                    jo.getString("messageProperties"),
                    jo.getString("messageContent"));
        }

        @Override
        public boolean configure(StaplerRequest sr, JSONObject formData) throws FormException {
            setProviderName(formData.optString("providerName"));
            setMessageType(MESSAGE_TYPE.fromString(formData.optString("messageType")));
            setMessageProperties(formData.optString("messageProperties"));
            setMessageContent(formData.optString("messageContent"));
            try {
                new CIMessageNotifier(getProviderName(),
                        getMessageType(),
                        getMessageProperties(),
                        getMessageContent());
            } catch (Exception e) {
                throw new FormException("Failed to initialize notifier - check your global notifier configuration settings", e, "");
            }
            save();
            return super.configure(sr, formData);
        }

        @Override
        public String getDisplayName() {
            return "CI Notifier";
        }

        public ListBoxModel doFillProviderNameItems() {
            ListBoxModel items = new ListBoxModel();
            for (MessagingProvider provider: GlobalCIConfiguration.get().getConfigs()) {
                items.add(provider.getName());
            }
            return items;
        }

        public ListBoxModel doFillMessageTypeItems(@QueryParameter String messageType) {
            MESSAGE_TYPE current = MESSAGE_TYPE.fromString(messageType);
            ListBoxModel items = new ListBoxModel();
            for (MESSAGE_TYPE t : MESSAGE_TYPE.values()) {
                items.add(new ListBoxModel.Option(t.toDisplayName(), t.name(), (t == current) || items.size() == 0));
            }
            return items;
        }

        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }
    }
}
