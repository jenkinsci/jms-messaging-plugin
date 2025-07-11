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

import java.util.Objects;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class ActiveMQPublisherProviderData extends ActiveMQProviderData {
    private static final long serialVersionUID = -2179136605130421113L;

    private String messageProperties;
    private String messageContent;
    private Boolean failOnError = false;
    private Integer timeToLiveMinutes = 0;

    @DataBoundConstructor
    public ActiveMQPublisherProviderData() {
    }

    public ActiveMQPublisherProviderData(String name) {
        this(name, null);
    }

    public ActiveMQPublisherProviderData(String name, MessagingProviderOverrides overrides) {
        super(name, overrides);
    }

    public ActiveMQPublisherProviderData(String name, MessagingProviderOverrides overrides, String messageProperties,
            String messageContent, Boolean failOnError, Integer timeToLiveMinutes) {
        this(name, overrides);
        setMessageProperties(messageProperties);
        setMessageContent(messageContent);
        setFailOnError(failOnError);
        setTimeToLiveMinutes(timeToLiveMinutes);
    }

    public String getMessageProperties() {
        return messageProperties;
    }

    @DataBoundSetter
    public void setMessageProperties(String messageProperties) {
        this.messageProperties = Util.fixEmpty(messageProperties);
    }

    public String getMessageContent() {
        return messageContent;
    }

    @DataBoundSetter
    public void setMessageContent(String messageContent) {
        this.messageContent = Util.fixEmpty(messageContent);
    }

    public Boolean getFailOnError() {
        return failOnError;
    }

    @DataBoundSetter
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public Integer getTimeToLiveMinutes() {
        return timeToLiveMinutes;
    }

    @DataBoundSetter
    public void setTimeToLiveMinutes(Integer timeToLiveMinutes) {
        this.timeToLiveMinutes = timeToLiveMinutes;
    }

    @Override
    public Descriptor<ProviderData> getDescriptor() {
        return Jenkins.get().getDescriptorByType(ActiveMQPublisherProviderDataDescriptor.class);
    }

    @Override
    public boolean equals(Object that) {
        if (!super.equals(that)) {
            return false;
        }

        ActiveMQPublisherProviderData thatp = (ActiveMQPublisherProviderData) that;
        return Objects.equals(this.name, thatp.name) && Objects.equals(this.overrides, thatp.overrides)
                && Objects.equals(this.messageProperties, thatp.messageProperties)
                && Objects.equals(this.messageContent, thatp.messageContent)
                && Objects.equals(this.failOnError, thatp.failOnError)
                && Objects.equals(this.timeToLiveMinutes, thatp.timeToLiveMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), messageProperties, messageContent, failOnError, timeToLiveMinutes);
    }

    @Extension
    @Symbol("activeMQPublisher")
    public static class ActiveMQPublisherProviderDataDescriptor extends ActiveMQProviderDataDescriptor {

        @Override
        public @Nonnull String getDisplayName() {
            return "ActiveMQ Publisher Provider Data";
        }

        @Override
        public ActiveMQPublisherProviderData newInstance(StaplerRequest2 sr, JSONObject jo) {
            MessagingProviderOverrides mpo = null;
            if (!jo.getJSONObject("overrides").isNullObject()) {
                mpo = new MessagingProviderOverrides(jo.getJSONObject("overrides").getString("topic"));
            }
            return new ActiveMQPublisherProviderData(jo.getString("name"), mpo, jo.getString("messageProperties"),
                    jo.getString("messageContent"), jo.getBoolean("failOnError"), jo.getInt("timeToLiveMinutes"));
        }

        public String getConfigPage() {
            return "amq-publisher.jelly";
        }

    }
}
