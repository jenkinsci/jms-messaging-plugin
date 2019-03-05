package com.redhat.jenkins.plugins.ci.provider.data;

import com.redhat.utils.MessageUtils;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;

import java.io.Serializable;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundSetter;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;

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

    protected String name;

    protected transient JMSMessagingProvider provider;

    public static final MessageUtils.MESSAGE_TYPE DEFAULT_MESSAGE_TYPE = MessageUtils.MESSAGE_TYPE.Custom;

    public ProviderData() {}

    public ProviderData(String name) {
        this.name = name;
        setProvider();
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
        setProvider();
    }

    public abstract boolean hasOverrides();
    public abstract String getSubscriberTopic();
    public abstract String getPublisherTopic();

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

    private void setProvider() {
        provider = GlobalCIConfiguration.get().getProvider(name);
    }

    public abstract static class ProviderDataDescriptor extends Descriptor<ProviderData> {
        public static ExtensionList<ProviderDataDescriptor> all() {
            return Jenkins.getInstance().getExtensionList(ProviderDataDescriptor.class);
        }
    }
}
