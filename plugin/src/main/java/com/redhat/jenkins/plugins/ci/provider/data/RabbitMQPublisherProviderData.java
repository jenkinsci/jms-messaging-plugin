package com.redhat.jenkins.plugins.ci.provider.data;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.utils.MessageUtils;
import com.redhat.utils.MessageUtils.MESSAGE_TYPE;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

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
public class RabbitMQPublisherProviderData extends RabbitMQProviderData {
    private static final long serialVersionUID = -2179136605130421113L;

    private String messageContent;
    private Boolean failOnError = false;
    // Fedora messaging related fields
    private Boolean fedoraMessaging = false;
    private Integer severity = 20;
    private String schema;

    @DataBoundConstructor
    public RabbitMQPublisherProviderData() {
    }

    public RabbitMQPublisherProviderData(String name) {
        this(name, null);
    }

    @Override
    public String getSubscriberTopic() {
        return "";
    }

    @Override
    public String getPublisherTopic() {
        return "";
    }

    public RabbitMQPublisherProviderData(String name, MessagingProviderOverrides overrides) {
        super(name, overrides);
    }

    public RabbitMQPublisherProviderData(String name, MessagingProviderOverrides overrides, String messageContent,
                                         Boolean failOnError, Boolean fedoraMessaging, Integer severity, String schema) {
        this(name, overrides);
        this.messageContent = messageContent;
        this.failOnError = failOnError;
        this.fedoraMessaging = fedoraMessaging;
        this.severity = severity;
        this.schema = schema;
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

    public Boolean isFedoraMessaging() {
        return fedoraMessaging;
    }

    @DataBoundSetter
    public void setFedoraMessaging(boolean fedoraMessaging) {
        this.fedoraMessaging = fedoraMessaging;
    }

    public Integer getSeverity() {
        return severity;
    }

    @DataBoundSetter
    public void setSeverity(Integer severity) {
        this.severity = severity;
    }

    public String getSchema() {
        return schema;
    }

    @DataBoundSetter
    public void setSchema(String schema) {
        this.schema = schema;
    }

    @Override
    public Descriptor<ProviderData> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(RabbitMQPublisherProviderDataDescriptor.class);
    }

    @Override
    public boolean equals(Object that){
        if (!super.equals(that)) {
            return false;
        }

        RabbitMQPublisherProviderData thatp = (RabbitMQPublisherProviderData)that;
        return (this.name != null ? this.name.equals(thatp.name) : thatp.name == null) &&
               (this.overrides != null ? this.overrides.equals(thatp.overrides) : thatp.overrides == null) &&
               (this.messageContent != null ? this.messageContent.equals(thatp.messageContent) : thatp.messageContent == null) &&
               (this.failOnError != null ? this.failOnError.equals(thatp.failOnError) : thatp.failOnError == null) &&
               (this.fedoraMessaging != null ? this.fedoraMessaging.equals(thatp.fedoraMessaging) : thatp.fedoraMessaging == null) &&
               (this.schema != null ? this.schema.equals(thatp.schema) : thatp.schema == null);
    }

    @Extension
    @Symbol("rabbitMQPublisher")
    public static class RabbitMQPublisherProviderDataDescriptor extends RabbitMQProviderDataDescriptor {

        @Override
        public String getDisplayName() {
            return "RabbitMQ Publisher Provider Data";
        }

        @Override
        public RabbitMQPublisherProviderData newInstance(StaplerRequest sr, JSONObject jo) {
            MessagingProviderOverrides mpo = null;
            Boolean fedoraMessaging = false;
            Integer severity = 20;
            String schema = "";
            if (!jo.getJSONObject("overrides").isNullObject()) {
                mpo = new MessagingProviderOverrides(jo.getJSONObject("overrides").getString("topic"));
            }
            if (!jo.getJSONObject("fedoraMessagingFields").isNullObject()) {
                fedoraMessaging = true;
                severity = jo.getJSONObject("fedoraMessagingFields").getInt("severity");
                schema = jo.getJSONObject("fedoraMessagingFields").getString("schema");
            }
            return new RabbitMQPublisherProviderData(
                    jo.getString("name"),
                    mpo,
                    jo.getString("messageContent"),
                    jo.getBoolean("failOnError"),
                    fedoraMessaging,
                    severity,
                    schema);
        }

        public ListBoxModel doFillMessageTypeItems(@QueryParameter String messageType) {
            return MessageUtils.doFillMessageTypeItems(messageType);
        }

        public MESSAGE_TYPE getDefaultMessageType() {
            return DEFAULT_MESSAGE_TYPE;
        }

        public String getConfigPage() {
            return "rabbitmq-publisher.jelly";
        }

    }
}
