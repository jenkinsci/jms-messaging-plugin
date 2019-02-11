package com.redhat.jenkins.plugins.ci.provider.data;

import org.kohsuke.stapler.DataBoundSetter;

import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import com.redhat.jenkins.plugins.ci.messaging.topics.DefaultTopicProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.TopicProvider.TopicProviderDescriptor;


public abstract class ActiveMQProviderData extends ProviderData {

    private static final long serialVersionUID = -5668984139637658338L;

    protected MessagingProviderOverrides overrides;

    public ActiveMQProviderData() {}

    public ActiveMQProviderData(String name) {
        this(name, null);
    }

    public ActiveMQProviderData(String name, MessagingProviderOverrides overrides) {
        super(name);
        this.overrides = overrides;
    }

    public MessagingProviderOverrides getOverrides() {
        return overrides;
    }

    @DataBoundSetter
    public void setOverrides(MessagingProviderOverrides overrides) {
        this.overrides = overrides;
    }

    public boolean hasOverrides() {
        return !(((ActiveMqMessagingProvider)provider).getTopicProvider() instanceof DefaultTopicProvider);
    }

    public String getSubscriberTopic() {
        TopicProviderDescriptor tpd = (TopicProviderDescriptor) ((ActiveMqMessagingProvider)provider).getTopicProvider().getDescriptor();
        return tpd.generateSubscriberTopic();
    }

    public String getPublisherTopic() {
        TopicProviderDescriptor tpd = (TopicProviderDescriptor) ((ActiveMqMessagingProvider)provider).getTopicProvider().getDescriptor();
        return tpd.generatePublisherTopic();
    }

    public abstract static class ActiveMQProviderDataDescriptor extends ProviderDataDescriptor {
    }
}
