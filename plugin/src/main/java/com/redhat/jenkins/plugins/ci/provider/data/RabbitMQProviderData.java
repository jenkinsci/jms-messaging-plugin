package com.redhat.jenkins.plugins.ci.provider.data;

import com.redhat.jenkins.plugins.ci.messaging.MessagingProviderOverrides;
import org.kohsuke.stapler.DataBoundSetter;

public abstract class RabbitMQProviderData extends ProviderData {


    private static final long serialVersionUID = -2179136601230421113L;

    protected MessagingProviderOverrides overrides;

    public RabbitMQProviderData() {}

    public RabbitMQProviderData(String name, MessagingProviderOverrides overrides) {
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

    public abstract static class RabbitMQProviderDataDescriptor extends ProviderDataDescriptor {
    }

}
