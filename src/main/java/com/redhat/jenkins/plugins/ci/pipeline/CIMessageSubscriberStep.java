package com.redhat.jenkins.plugins.ci.pipeline;

import com.redhat.jenkins.plugins.ci.messaging.*;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQSubscriberProviderData;
import com.redhat.utils.MessageUtils;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.util.ListBoxModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import jenkins.util.Timer;

import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.collect.ImmutableSet;
import com.redhat.jenkins.plugins.ci.CIMessageSubscriberBuilder;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.FedMsgSubscriberProviderData;
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
public class CIMessageSubscriberStep extends Step {

    private String providerName;
    private MessagingProviderOverrides overrides;
    private String selector;
    private List<MsgCheck> checks = new ArrayList<MsgCheck>();
    private Integer timeout;

    @DataBoundConstructor
    public CIMessageSubscriberStep(final String providerName,
                                   final MessagingProviderOverrides overrides,
                                   final String selector,
                                   final Integer timeout,
                                   List<MsgCheck> checks) {
        super();
        this.providerName = providerName;
        this.overrides = overrides;
        this.selector = selector;
        this.timeout = timeout;
        if (checks == null) {
            checks = new ArrayList<>();
        }
        this.checks = checks;
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

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public List<MsgCheck> getChecks() {
        return checks;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    /**
     * Executes the waitForCIMessage step.
     */
    public static final class Execution extends AbstractStepExecutionImpl {

        Execution(CIMessageSubscriberStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Inject
        private transient CIMessageSubscriberStep step;
        private transient Future<?> task;

        @Override
        public boolean start() throws Exception {
            if (step.getProviderName() == null) {
                throw new Exception("Provider name not specified!");
            } else if (GlobalCIConfiguration.get().getProvider(step.getProviderName()) == null) {
                throw new Exception("Unrecognized provider name.");
            }

            task = Timer.get().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        ProviderData pd = null;
                        JMSMessagingProvider p = GlobalCIConfiguration.get().getProvider(step.getProviderName());
                        if (p instanceof ActiveMqMessagingProvider) {
                            ActiveMQSubscriberProviderData apd = new ActiveMQSubscriberProviderData(step.getProviderName());
                            apd.setOverrides(step.getOverrides());
                            apd.setSelector(step.getSelector());
                            apd.setChecks(step.getChecks());
                            apd.setTimeout(step.getTimeout());
                            pd = apd;
                        } else if (p instanceof FedMsgMessagingProvider) {
                            FedMsgSubscriberProviderData fpd = new FedMsgSubscriberProviderData(step.getProviderName());
                            fpd.setOverrides(step.getOverrides());
                            fpd.setChecks(step.getChecks());
                            fpd.setTimeout(step.getTimeout());
                            pd = fpd;
                        } else if (p instanceof RabbitMQMessagingProvider) {
                            RabbitMQSubscriberProviderData rpd = new RabbitMQSubscriberProviderData(step.getProviderName());
                            rpd.setOverrides(step.getOverrides());
                            rpd.setChecks(step.getChecks());
                            rpd.setTimeout(step.getTimeout());
                            pd = rpd;
                        }
                    CIMessageSubscriberBuilder subscriber = new CIMessageSubscriberBuilder(pd);
                        StepContext c = getContext();
                        String result = subscriber.waitforCIMessage(c.get(Run.class), c.get(Launcher.class), c.get(TaskListener.class));
                        if (result != null) {
                            getContext().onSuccess(result);
                        } else {
                            getContext().onFailure(new AbortException("Timeout waiting for message!"));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        getContext().onFailure(e);
                    }
}
            });
            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
            if (task != null) {
                getContext().get(TaskListener.class).getLogger().println("in stop of watcher");
                task.cancel(true);
                getContext().onFailure(cause);
            }
        }

        private static final long serialVersionUID = 1L;

    }

    /**
     * Adds the step as a workflow extension.
     */
    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {

        public ListBoxModel doFillProviderNameItems() {
            return MessageUtils.doFillProviderNameItems();
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, Launcher.class, TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return "waitForCIMessage";
        }

        @Override
        public String getDisplayName() {
            return Messages.SubscriberBuilder();
        }

    }
}
