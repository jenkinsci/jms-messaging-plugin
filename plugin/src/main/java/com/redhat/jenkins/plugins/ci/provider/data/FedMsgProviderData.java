package com.redhat.jenkins.plugins.ci.provider.data;

import org.kohsuke.stapler.DataBoundSetter;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;


public abstract class FedMsgProviderData extends ProviderData {

    private static final long serialVersionUID = -6927102664730250650L;

    protected MessagingProviderOverrides overrides;

    public FedMsgProviderData() {}

    public FedMsgProviderData(String name) {
        this(name, null);
    }

    public FedMsgProviderData(String name, MessagingProviderOverrides overrides) {
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
        return false;
    }

    public String getSubscriberTopic() {
        return "";
    }

    public String getPublisherTopic() {
        return "";
    }

    public abstract static class FedMsgProviderDataDescriptor extends ProviderDataDescriptor {
    }
}
