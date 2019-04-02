package com.redhat.jenkins.plugins.ci.provider.data;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.utils.MessageUtils;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 * Copyright (c) Valentin Titov
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

public class KafkaPublisherProviderData extends KafkaProviderData {

    private static final long serialVersionUID = 9038816269318064458L;

    private String messageContent;
    private Boolean failOnError = false;

    @DataBoundConstructor
    public KafkaPublisherProviderData() {}

    public KafkaPublisherProviderData(String name) {
        this(name, null);
    }

    public KafkaPublisherProviderData(String name, MessagingProviderOverrides overrides) {
        super(name, overrides);
    }

    public KafkaPublisherProviderData(String name, MessagingProviderOverrides overrides, String messageContent, Boolean failOnError) {
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
        return Jenkins.getInstance().getDescriptorByType(KafkaPublisherProviderDataDescriptor.class);
    }

    @Override
    public boolean equals(Object that){
        if (!super.equals(that)) {
            return false;
        }

        KafkaPublisherProviderData thatp = (KafkaPublisherProviderData)that;
        return (this.name != null ? this.name.equals(thatp.name) : thatp.name == null) &&
                (this.overrides != null ? this.overrides.equals(thatp.overrides) : thatp.overrides == null) &&
                (this.messageContent != null ? this.messageContent.equals(thatp.messageContent) : thatp.messageContent == null) &&
                (this.failOnError != null ? this.failOnError.equals(thatp.failOnError) : thatp.failOnError == null);
    }

    @Extension
    public static class KafkaPublisherProviderDataDescriptor extends KafkaProviderDataDescriptor {

        @Override
        public String getDisplayName() {
            return "Kafka Publisher Provider Data";
        }

        @Override
        public KafkaPublisherProviderData newInstance(StaplerRequest sr, JSONObject jo) {
            MessagingProviderOverrides mpo = null;
            if (!jo.getJSONObject("overrides").isNullObject()) {
                mpo = new MessagingProviderOverrides(jo.getJSONObject("overrides").getString("topic"));
            }
            return new KafkaPublisherProviderData(
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
            return "kafka-publisher.jelly";
        }

    }

}
