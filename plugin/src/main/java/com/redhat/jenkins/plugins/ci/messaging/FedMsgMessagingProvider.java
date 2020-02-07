package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

import com.redhat.jenkins.plugins.ci.messaging.data.FedmsgMessage;
import com.redhat.jenkins.plugins.ci.provider.data.FedMsgProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

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
public class FedMsgMessagingProvider extends JMSMessagingProvider {

    private static final long serialVersionUID = 82154526798596907L;

    private String hubAddr;
    private String pubAddr;
    private String topic;

    @DataBoundConstructor
    public FedMsgMessagingProvider(String name, String hubAddr,
                                   String pubAddr, String topic) {
        this.name = name;
        this.hubAddr = hubAddr;
        this.pubAddr = pubAddr;
        this.topic = topic;
    }

    public String getHubAddr() {
        return hubAddr;
    }

    public String petHubAddr() {
        return pubAddr;
    }

    public String getTopic() {
        return topic;
    }

    public String formatMessage(FedmsgMessage data) {
        return data.getBodyJson();
    }

    @Override
    public JMSMessagingWorker createWorker(ProviderData pdata, String jobname) {
        return new FedMsgMessagingWorker(this, ((FedMsgProviderData)pdata).getOverrides(), jobname);
    }

    @Override
    public JMSMessageWatcher createWatcher(String jobname) {
        return new FedMsgMessageWatcher(jobname);
    }

    @Override
    public Descriptor<JMSMessagingProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(FedMsgMessagingProviderDescriptor.class);
    }

    public String getPubAddr() {
        return pubAddr;
    }

    @Extension
    public static class FedMsgMessagingProviderDescriptor extends MessagingProviderDescriptor {
        @Override
        public String getDisplayName() {
            return "FedMsg";
        }

    }

}
