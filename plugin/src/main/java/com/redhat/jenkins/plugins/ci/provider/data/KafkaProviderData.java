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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundSetter;

import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;

public abstract class KafkaProviderData extends ProviderData {
    private static final Logger log = Logger.getLogger(KafkaProviderData.class.getName());

    protected MessagingProviderOverrides overrides;
    protected String properties;

    public KafkaProviderData() {
    }

    public KafkaProviderData(String name) {
        this(name, null, null);
    }

    public KafkaProviderData(String name, MessagingProviderOverrides overrides, String properties) {
        super(name);
        this.overrides = overrides;
        this.properties = properties;
    }

    public MessagingProviderOverrides getOverrides() {
        return overrides;
    }

    @DataBoundSetter
    public void setOverrides(MessagingProviderOverrides overrides) {
        this.overrides = overrides;
    }

    public boolean hasOverrides() {
        return overrides != null;
    }

    public String getProperties() {
        return properties;
    }

    public Properties mergeProperties(Properties props) {
        try {
            props.load(IOUtils.toInputStream(properties == null ? "" : properties, Charset.defaultCharset()));
        } catch (IOException e) {
            log.log(Level.WARNING, String.format("bad properties: %s", properties));
        }
        return props;
    }

    @DataBoundSetter
    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getSubscriberTopic() {
        if (hasOverrides()) {
            return overrides.getTopic();
        } else {
            return ((KafkaMessagingProvider) provider).getTopic();
        }
    }

    public String getPublisherTopic() {
        if (hasOverrides()) {
            return overrides.getTopic();
        } else {
            return ((KafkaMessagingProvider) provider).getTopic();
        }
    }

    public abstract static class KafkaProviderDataDescriptor extends ProviderDataDescriptor {
    }
}
