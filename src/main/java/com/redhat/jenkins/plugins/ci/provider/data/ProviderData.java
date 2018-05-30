package com.redhat.jenkins.plugins.ci.provider.data;

import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;

import java.io.Serializable;

import jenkins.model.Jenkins;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.DefaultTopicProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.TopicProvider.TopicProviderDescriptor;

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
public abstract class ProviderData implements Describable<ProviderData>, Serializable  {

    private static final long serialVersionUID = -5475213587386619340L;

    private String name;

    public ProviderData() {}

    public ProviderData(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean hasOverrides() {
        JMSMessagingProvider p = GlobalCIConfiguration.get().getProvider(name);
        return p instanceof ActiveMqMessagingProvider &&
               !(((ActiveMqMessagingProvider)p).getTopicProvider() instanceof DefaultTopicProvider);
    }

    public String getSubscriberTopic() {
        JMSMessagingProvider p = GlobalCIConfiguration.get().getProvider(name);
        if (p instanceof ActiveMqMessagingProvider) {
            TopicProviderDescriptor tpd = (TopicProviderDescriptor) ((ActiveMqMessagingProvider)p).getTopicProvider().getDescriptor();
            return tpd.generateSubscriberTopic();
        }
        return "";
    }

    public String getPublisherTopic() {
        JMSMessagingProvider p = GlobalCIConfiguration.get().getProvider(name);
        if (p instanceof ActiveMqMessagingProvider) {
            TopicProviderDescriptor tpd = (TopicProviderDescriptor) ((ActiveMqMessagingProvider)p).getTopicProvider().getDescriptor();
            return tpd.generatePublisherTopic();
        }
        return "";
    }

    @Override
    public boolean equals(Object that){
        if (this == that) {
            return true;
        }

        if (that == null || this.getClass() != that.getClass()) {
            return false;
        }

        ProviderData thatp = (ProviderData)that;
        return this.name != null ? this.name.equals(thatp.name) : thatp.name == null;
    }

    public abstract static class ProviderDataDescriptor extends Descriptor<ProviderData> {
        public static ExtensionList<ProviderDataDescriptor> all() {
            return Jenkins.getInstance().getExtensionList(ProviderDataDescriptor.class);
        }
    }
}
