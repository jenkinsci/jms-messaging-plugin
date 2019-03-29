package com.redhat.jenkins.plugins.ci.provider.data;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundSetter;

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

public abstract class KafkaProviderData extends ProviderData {

    private static final long serialVersionUID = -2699230090866964683L;

    protected MessagingProviderOverrides overrides;

    public KafkaProviderData() {}

    public KafkaProviderData(String name) {
        this(name, null);
    }

    public KafkaProviderData(String name, MessagingProviderOverrides overrides) {
        super(name);
        this.overrides = overrides;
    }

    public MessagingProviderOverrides getOverrides() {
        return overrides;
    }

    @DataBoundSetter
    public void setOverrides(MessagingProviderOverrides overrides) {
        this.overrides = overrides;
    }

    @Override
    public boolean hasOverrides() {
        return false; // FIXME
    }

    @Override
    public String getSubscriberTopic() {
        return ""; // FIXME
    }

    @Override
    public String getPublisherTopic() {
        return ""; // FIXME
    }

    public abstract static class KafkaProviderDataDescriptor extends ProviderDataDescriptor {
    }

}
