package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.model.Descriptor;

import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

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

    private String  hubAddr;
    private String  pubAddr;
    private String  topic;
    private boolean shouldSign;
    private String  certificateFile;
    private String  keystoreFile;

    @DataBoundConstructor
    public FedMsgMessagingProvider(String name, String hubAddr,
                                   String pubAddr, String topic,
                                   boolean shouldSign,
                                   String certificateFile,
                                   String keystoreFile) {
        this.name = name;
        this.hubAddr = hubAddr;
        this.pubAddr = pubAddr;
        this.topic = topic;
        this.shouldSign = shouldSign;
        this.certificateFile = certificateFile;
        this.keystoreFile = keystoreFile;
    }

    public boolean getShouldSign() {
        return shouldSign;
    }

    public String getHubAddr() {
        return hubAddr;
    }

    public String getTopic() {
        return topic;
    }

    public String getPubAddr() {
        return pubAddr;
    }

    public String getCertificateFile() {
        return certificateFile;
    }

    public String getKeystoreFile() {
        return keystoreFile;
    }


    @Override
    public JMSMessagingWorker createWorker(MessagingProviderOverrides overrides, String jobname) {
        return new FedMsgMessagingWorker(this, overrides, jobname);
    }

    @Override
    public Descriptor<JMSMessagingProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(FedMsgMessagingProviderDescriptor.class);
    }

    @Extension
    public static class FedMsgMessagingProviderDescriptor extends MessagingProviderDescriptor {
        private final Logger log = Logger.getLogger(FedMsgMessagingProviderDescriptor.class.getName());

        @Override
        public String getDisplayName() {
            return "FedMsg";
        }

    }
}
