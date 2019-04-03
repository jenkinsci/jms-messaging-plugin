package com.redhat.jenkins.plugins.ci.threads;

import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.FedMsgMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

public class CITriggerThreadFactory {

    public static CITriggerThread createCITriggerThread(JMSMessagingProvider messagingProvider, ProviderData providerData, String jobname, int instance) {
        if (messagingProvider instanceof ActiveMqMessagingProvider) {
            return new ActiveMqTriggerThread(messagingProvider, providerData, jobname ,instance);
        } else if (messagingProvider instanceof FedMsgMessagingProvider) {
            return new FedMsgTriggerThread(messagingProvider, providerData, jobname ,instance);
        } else {
            return new CITriggerThread(messagingProvider, providerData, jobname ,instance);
        }
    }
}
