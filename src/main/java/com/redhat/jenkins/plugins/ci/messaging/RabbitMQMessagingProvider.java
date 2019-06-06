package com.redhat.jenkins.plugins.ci.messaging;

import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQProviderData;
import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.logging.Logger;

public class RabbitMQMessagingProvider extends JMSMessagingProvider {

    private static final Logger log = Logger.getLogger(RabbitMQMessagingProvider.class.getName());

    @Override
    public JMSMessagingWorker createWorker(ProviderData pdata, String jobname) {
        return new RabbitMQMessagingWorker(this, ((RabbitMQProviderData)pdata).getOverrides(), jobname);
    }

    @Override
    public JMSMessageWatcher createWatcher(String jobname) {
        return new RabbitMQMessageWatcher(jobname);
    }

    @Override
    public Descriptor<JMSMessagingProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(RabbitMQMessagingProvider.RabbitMQProviderDescriptor.class);
    }

    @Extension
    public static class RabbitMQProviderDescriptor extends MessagingProviderDescriptor {
        @Override
        public String getDisplayName() {
            return "RabbitMQ";
        }

    }
}
