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

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.collect.ImmutableSet;
import com.redhat.jenkins.plugins.ci.CIMessageNotifier;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQPublisherProviderData;
import com.redhat.utils.MessageUtils;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.util.Timer;

public class CIMessageSenderStep extends Step {

    private ProviderData providerData;

    @DataBoundConstructor
    public CIMessageSenderStep(final ProviderData providerData) {
        super();
        this.providerData = providerData;
    }

    public ProviderData getProviderData() {
        return providerData;
    }

    public void setProviderDaa(ProviderData providerData) {
        this.providerData = providerData;
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
            if (step.getProviderData() == null) {
                throw new Exception("Provider data not specified!");
            } else if (GlobalCIConfiguration.get().getProvider(step.getProviderData().getName()) == null) {
                throw new Exception("Unrecognized provider name: " + step.getProviderData().getName());
            }

            StepContext c = getContext();
            task = Timer.get().submit(() -> {
                try {
                    ProviderData pd = null;
                    JMSMessagingProvider p = GlobalCIConfiguration.get().getProvider(step.getProviderData().getName());
                    if (p instanceof ActiveMqMessagingProvider) {
                        pd = (ActiveMQPublisherProviderData) step.getProviderData();
                    } else if (p instanceof KafkaMessagingProvider) {
                        pd = (KafkaPublisherProviderData) step.getProviderData();
                    } else if (p instanceof RabbitMQMessagingProvider) {
                        pd = (RabbitMQPublisherProviderData) step.getProviderData();
                    }
                    CIMessageNotifier notifier = new CIMessageNotifier(pd);
                    SendResult status = notifier.doMessageNotifier(c.get(Run.class), null, c.get(TaskListener.class));
                    if (status.isSucceeded()) {
                        c.onSuccess(status);
                    } else {
                        c.onFailure(new Exception("Exception sending message. Please check server logs."));
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    c.onFailure(e);
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

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
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

        public ListBoxModel doFillProviderNameItems() {
            return MessageUtils.doFillProviderNameItems();
        }
    }
}
