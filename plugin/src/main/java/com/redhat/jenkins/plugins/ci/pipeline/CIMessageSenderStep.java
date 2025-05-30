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
package com.redhat.jenkins.plugins.ci.pipeline;

import com.google.common.collect.ImmutableSet;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.messaging.FedMsgMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.FedMsgPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQPublisherProviderData;
import com.redhat.utils.MessageUtils;
import com.redhat.utils.MessageUtils.MESSAGE_TYPE;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Future;

import static com.redhat.jenkins.plugins.ci.provider.data.ProviderData.DEFAULT_MESSAGE_TYPE;

public class CIMessageSenderStep extends Step {

    private String providerName;
    private MessagingProviderOverrides overrides;
    private MESSAGE_TYPE messageType;
    private String messageProperties;
    private String messageContent;
    private boolean failOnError;
    private Integer timeToLiveMinutes;

    public CIMessageSenderStep(final String providerName,
                               final MessagingProviderOverrides overrides,
                               final MESSAGE_TYPE messageType,
                               final String messageProperties,
                               final String messageContent) {
        this(providerName, overrides, messageType, messageProperties, messageContent, false, 0);
    }

    public CIMessageSenderStep(final String providerName,
                               final MessagingProviderOverrides overrides,
                               final MESSAGE_TYPE messageType,
                               final String messageProperties,
                               final String messageContent,
			       final Integer timeToLiveMinutes) {
        this(providerName, overrides, messageType, messageProperties, messageContent, false, timeToLiveMinutes);
    }

    @DataBoundConstructor
    public CIMessageSenderStep(final String providerName,
                               final MessagingProviderOverrides overrides,
                               final MESSAGE_TYPE messageType,
                               final String messageProperties,
                               final String messageContent,
                               Boolean failOnError,
                               Integer timeToLiveMinutes) {
        super();
        this.providerName = providerName;
        this.overrides = overrides;
        this.messageType = messageType;
        this.messageProperties = messageProperties;
        this.messageContent = messageContent;
        if (failOnError == null) {
            failOnError = false;
        }
        this.failOnError = failOnError;
        if (timeToLiveMinutes == null) {
            timeToLiveMinutes = 0;
        }
        this.timeToLiveMinutes = timeToLiveMinutes;
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

    public boolean getFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public Integer getTimeToLiveMinutes() {
        return timeToLiveMinutes;
    }

    public void setTimeToLiveMinutes(Integer timeToLiveMinutes) {
        this.timeToLiveMinutes = timeToLiveMinutes;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new CIMessageSenderStep.Execution(this, context);
    }

    /**
     * Executes the sendCIMessage step.
     */
    public static final class Execution extends AbstractStepExecutionImpl {

        Execution(CIMessageSenderStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Inject
        private final transient CIMessageSenderStep step;
        private transient Future<?> task;

        @Override
        public boolean start() throws Exception {
            if (step.getProviderName() == null) {
                throw new Exception("Provider name not specified!");
            } else if (GlobalCIConfiguration.get().getProvider(step.getProviderName()) == null) {
                throw new Exception("Unrecognized provider name: " + step.getProviderName());
            }

            task = Timer.get().submit(() -> {
                try {
                    ProviderData pd = null;
                    JMSMessagingProvider p = GlobalCIConfiguration.get().getProvider(step.getProviderName());
                    if (p instanceof ActiveMqMessagingProvider) {
                        ActiveMQPublisherProviderData apd = new ActiveMQPublisherProviderData(step.getProviderName());
                        apd.setOverrides(step.getOverrides());
                        apd.setMessageType(step.getMessageType());
                        apd.setMessageProperties(step.getMessageProperties());
                        apd.setMessageContent(step.getMessageContent());
                        apd.setFailOnError(step.getFailOnError());
                        apd.setTimeToLiveMillis(step.getTimeToLiveMinutes() * 60 * 1000);
                        pd = apd;
                    } else if (p instanceof FedMsgMessagingProvider) {
                        FedMsgPublisherProviderData fpd = new FedMsgPublisherProviderData(step.getProviderName());
                        fpd.setOverrides(step.getOverrides());
                        fpd.setMessageContent(step.getMessageContent());
                        fpd.setFailOnError(step.getFailOnError());
                        pd = fpd;
                    } else if (p instanceof RabbitMQMessagingProvider) {
                        RabbitMQPublisherProviderData rpd = new RabbitMQPublisherProviderData(step.getProviderName());
                        rpd.setOverrides(step.getOverrides());
                        rpd.setMessageContent(step.getMessageContent());
                        rpd.setFailOnError(step.getFailOnError());
                        pd = rpd;
                    } else if (p instanceof KafkaMessagingProvider) {
                        KafkaPublisherProviderData kpd = new KafkaPublisherProviderData(step.getProviderName());
                        kpd.setOverrides(step.getOverrides());
                        kpd.setMessageContent(step.getMessageContent());
                        kpd.setFailOnError(step.getFailOnError());
                        pd = kpd;
                    }
                    CIMessageNotifier notifier = new CIMessageNotifier(pd);
                    StepContext c = getContext();
                    SendResult status = notifier.doMessageNotifier(c.get(Run.class), null, c.get(TaskListener.class));
                    if (status.isSucceeded()) {
                        getContext().onSuccess(status);
                    } else {
                        getContext().onFailure(new Exception("Exception sending message. Please check server logs."));
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    getContext().onFailure(e);
                }
            });

            return false;
        }

        @Override
        public void stop(@Nonnull Throwable throwable) {
            task.cancel(true);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Adds the step as a workflow extension.
     */
    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return "sendCIMessage";
        }

        @Override
        public @Nonnull String getDisplayName() {
            return Messages.MessageNotifier();
        }

        public ListBoxModel doFillMessageTypeItems(@QueryParameter String messageType) {
            return MessageUtils.doFillMessageTypeItems(messageType);
        }

        public ListBoxModel doFillProviderNameItems() {
            return MessageUtils.doFillProviderNameItems();
        }


        public MESSAGE_TYPE getDefaultMessageType() {
            return DEFAULT_MESSAGE_TYPE;
        }

    }
}
