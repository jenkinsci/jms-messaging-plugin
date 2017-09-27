package com.redhat.jenkins.plugins.ci.pipeline;

import com.google.common.collect.ImmutableSet;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import hudson.util.ListBoxModel;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.redhat.jenkins.plugins.ci.CIMessageSubscriberBuilder;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

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
        private transient Future task;

        @Override
        public boolean start() throws Exception {
            if (step.getProviderName() == null) {
                throw new Exception("providerName not specified!");
            }
            task = Timer.get().submit(new Runnable() {
                @Override
                public void run() {
                    int timeout = CIMessageSubscriberBuilder.DEFAULT_TIMEOUT_IN_MINUTES;
                    if (step.getTimeout() != null && step.getTimeout() > 0) {
                        timeout = step.getTimeout();
                    }
                    CIMessageSubscriberBuilder builder = new CIMessageSubscriberBuilder(step.getProviderName(),
                            step.getOverrides(),
                            step.getSelector(),
                            step.getChecks(),
                            timeout
                            );
                    try {
                        String msg = builder.waitforCIMessage(getContext().get(Run.class), getContext().get(Launcher.class),
                                getContext().get(TaskListener.class));
                        if (msg == null) {
                            getContext().onFailure(new AbortException("Timeout waiting for message!"));
                        } else {
                            getContext().onSuccess(msg);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
            if (task != null) {
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
            ListBoxModel items = new ListBoxModel();
            for (JMSMessagingProvider provider: GlobalCIConfiguration.get().getConfigs()) {
                items.add(provider.getName());
            }
            return items;
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
