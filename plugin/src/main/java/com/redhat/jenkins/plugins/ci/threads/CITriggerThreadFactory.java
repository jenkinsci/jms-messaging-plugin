package com.redhat.jenkins.plugins.ci.threads;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingProvider;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

public class CITriggerThreadFactory {

    public static CITriggerThread createCITriggerThread(JMSMessagingProvider messagingProvider,
            ProviderData providerData, String jobname, CIBuildTrigger cibt, int instance) {
        if (messagingProvider instanceof ActiveMqMessagingProvider) {
            return new ActiveMqTriggerThread(messagingProvider, providerData, jobname, cibt, instance);
        } else if (messagingProvider instanceof KafkaMessagingProvider) {
            return new KafkaTriggerThread(messagingProvider, providerData, jobname, cibt, instance);
            // } else if (messagingProvider instanceof RabbitMQMessagingProvider) {
            // return new RabbitMqTriggerThread(messagingProvider, providerData, jobname, cibt, instance);
        } else {
            return new CITriggerThread(messagingProvider, providerData, jobname, cibt, instance);
        }
    }
}
