package com.redhat.jenkins.plugins.ci.messaging;

import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;

import java.io.Serializable;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

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
public abstract class JMSMessagingProvider implements Describable<JMSMessagingProvider>, Serializable {

    protected String name;
    protected String topic;
    protected static final Logger log = Logger.getLogger(JMSMessagingProvider.class.getName());
    public final static String DEFAULT_PROVIDERNAME = "default";

    public String getName() {
        return name;
    }
    public String getTopic() {
        return topic;
    }

    public JMSMessagingWorker createWorker(String jobname) {
        return createWorker(null, jobname);
    }

    public abstract JMSMessagingWorker createWorker(MessagingProviderOverrides overrides, String jobname);
    public abstract JMSMessageWatcher  createWatcher();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        JMSMessagingProvider that = (JMSMessagingProvider) o;

        return new EqualsBuilder()
                .append(name, that.name)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(name)
                .toHashCode();
    }

    public abstract static class MessagingProviderDescriptor extends Descriptor<JMSMessagingProvider> {

        public static ExtensionList<MessagingProviderDescriptor> all() {
            return Jenkins.getInstance().getExtensionList(MessagingProviderDescriptor.class);
        }
    }
}
