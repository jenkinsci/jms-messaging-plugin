package com.redhat.jenkins.plugins.ci;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.MessageUtils;
import com.redhat.utils.MessageUtils.MESSAGE_TYPE;

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
 */public class CIMessageNotifier extends Notifier {
     private static final Logger log = Logger.getLogger(CIMessageNotifier.class.getName());

     private static final String BUILDER_NAME = Messages.MessageNotifier();

     private transient String providerName;
     private transient MessagingProviderOverrides overrides;
     private transient MESSAGE_TYPE messageType;
     private transient String messageProperties;
     private transient String messageContent;
     private transient boolean failOnError = false;
     private ProviderData providerData;

     @DataBoundConstructor
     public CIMessageNotifier() {}

     public CIMessageNotifier(ProviderData providerData) {
         super();
         this.providerData = providerData;
     }

     public String getProviderName() {
         return providerName;
     }

     public void setProviderName(String providerName) {
         this.providerName = providerName;
     }

     public MessagingProviderOverrides getOverrides() {
         return overrides;
     }

     public void setOverrides(MessagingProviderOverrides overrides) {
         this.overrides = overrides;
     }

     public MESSAGE_TYPE getMessageType() {
         return messageType;
     }

     public void setMessageType(MESSAGE_TYPE messageType) {
         this.messageType = messageType;
     }

     public String getMessageProperties() {
         return messageProperties;
     }

     public void setMessageProperties(String messageProperties) {
         this.messageProperties = messageProperties;
     }

     public String getMessageContent() {
         return messageContent;
     }

     public void setMessageContent(String messageContent) {
         this.messageContent = messageContent;
     }

     public boolean isFailOnError() {
         return failOnError;
     }

     public void setFailOnError(boolean failOnError) {
         this.failOnError = failOnError;
     }

     public ProviderData getProviderData() {
         return providerData;
     }

     @DataBoundSetter
     public void setProviderData(ProviderData providerData) {
         this.providerData = providerData;
     }

     @Override
     public DescriptorImpl getDescriptor() {
         return (DescriptorImpl) super.getDescriptor();
     }

     public BuildStepMonitor getRequiredMonitorService() {
         return BuildStepMonitor.NONE;
     }

     public boolean needsToRunAfterFinalized() {
         return true;
     }

     public SendResult doMessageNotifier(Run<?, ?> run, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
         return MessageUtils.sendMessage(run, listener, providerData);
     }

     @Override
     public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
         return doMessageNotifier(build, launcher, listener).isSucceeded();
     }

     @Extension
     public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

         public DescriptorImpl() {
             load();
         }

         @SuppressWarnings("rawtypes")
         @Override
         public boolean isApplicable(Class<? extends AbstractProject> arg0) {
             return true;
         }

         @Override
         public CIMessageNotifier newInstance(StaplerRequest sr, JSONObject jo) {
             try {
                 // The provider name is at the root of the JSON object with a key of "" (this
                 // is because the select is not named in dropdownList.jelly). Move that into the
                 // provider data structure and then continue on.
                 jo.getJSONObject("providerData").put("name", jo.remove(""));
                 return (CIMessageNotifier)super.newInstance(sr, jo);
             } catch (hudson.model.Descriptor.FormException e) {
                 log.log(Level.SEVERE, "Unable to create new instance.", e);;
             }
             return null;
         }

         @Override
         public String getDisplayName() {
             return BUILDER_NAME;
         }
     }
 }
