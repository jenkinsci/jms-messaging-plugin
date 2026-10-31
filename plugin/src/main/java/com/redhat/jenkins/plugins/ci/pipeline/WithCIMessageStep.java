package com.redhat.jenkins.plugins.ci.pipeline;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQSubscriberProviderData;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;

/** Subscribe before running a request body, then return a matching response. */
public class WithCIMessageStep extends Step {
    private final ProviderData providerData;

    @DataBoundConstructor
    public WithCIMessageStep(ProviderData providerData) {
        this.providerData = providerData;
    }

    public ProviderData getProviderData() {
        return providerData;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new RabbitMQSubscriberBlockExecution(providerData, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        public List<ProviderData> getSubscriberProviders() {
            return GlobalCIConfiguration.get().getSubscriberProviders().stream()
                    .filter(RabbitMQSubscriberProviderData.class::isInstance).toList();
        }

        @Override
        public String getFunctionName() {
            return "withCIMessage";
        }

        @Override
        public @Nonnull String getDisplayName() {
            return "Subscribe to RabbitMQ, run a body, and await a response";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class);
        }
    }
}
