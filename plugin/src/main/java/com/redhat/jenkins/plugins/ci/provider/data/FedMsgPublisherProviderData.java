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
package com.redhat.jenkins.plugins.ci.provider.data;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.utils.MessageUtils;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

import javax.annotation.Nonnull;
import java.util.Objects;

public class FedMsgPublisherProviderData extends FedMsgProviderData {
    private static final long serialVersionUID = -2179136605130421113L;

    private String messageContent;
    private Boolean failOnError = false;

    @DataBoundConstructor
    public FedMsgPublisherProviderData() {
    }

    public FedMsgPublisherProviderData(String name) {
        this(name, null);
    }

    public FedMsgPublisherProviderData(String name, MessagingProviderOverrides overrides) {
        super(name, overrides);
    }

    public FedMsgPublisherProviderData(String name, MessagingProviderOverrides overrides, String messageContent, Boolean failOnError) {
        this(name, overrides);
        this.messageContent = messageContent;
        this.failOnError = failOnError;
    }

    public String getMessageContent() {
        return messageContent;
    }

    @DataBoundSetter
    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public Boolean isFailOnError() {
        return failOnError;
    }

    @DataBoundSetter
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    @Override
    public Descriptor<ProviderData> getDescriptor() {
        return Jenkins.get().getDescriptorByType(FedMsgPublisherProviderDataDescriptor.class);
    }

    @Override
    public boolean equals(Object that) {
        if (!super.equals(that)) {
            return false;
        }

        FedMsgPublisherProviderData thatp = (FedMsgPublisherProviderData) that;
        return Objects.equals(this.name, thatp.name) &&
                Objects.equals(this.overrides, thatp.overrides) &&
                Objects.equals(this.messageContent, thatp.messageContent) &&
                Objects.equals(this.failOnError, thatp.failOnError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), messageContent, failOnError);
    }

    @Extension
    @Symbol("fedmsgPublisher")
    public static class FedMsgPublisherProviderDataDescriptor extends FedMsgProviderDataDescriptor {

        @Override
        public @Nonnull String getDisplayName() {
            return "FedMsg Publisher Provider Data";
        }

        @Override
        public FedMsgPublisherProviderData newInstance(StaplerRequest2 sr, JSONObject jo) {
            MessagingProviderOverrides mpo = null;
            if (!jo.getJSONObject("overrides").isNullObject()) {
                mpo = new MessagingProviderOverrides(jo.getJSONObject("overrides").getString("topic"));
            }
            return new FedMsgPublisherProviderData(
                    jo.getString("name"),
                    mpo,
                    jo.getString("messageContent"),
                    jo.getBoolean("failOnError"));
        }

        public ListBoxModel doFillMessageTypeItems(@QueryParameter String messageType) {
            return MessageUtils.doFillMessageTypeItems(messageType);
        }

        public MessageUtils.MESSAGE_TYPE getDefaultMessageType() {
            return DEFAULT_MESSAGE_TYPE;
        }

        public String getConfigPage() {
            return "fedmsg-publisher.jelly";
        }
    }
}
