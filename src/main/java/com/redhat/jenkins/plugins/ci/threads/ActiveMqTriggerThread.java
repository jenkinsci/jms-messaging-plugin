package com.redhat.jenkins.plugins.ci.threads;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

public class ActiveMqTriggerThread extends CITriggerThread {

    public ActiveMqTriggerThread(JMSMessagingProvider messagingProvider, ProviderData providerData, String jobname, CIBuildTrigger cibt, int instance) {
        super(messagingProvider, providerData, jobname, cibt, instance);
    }
}
