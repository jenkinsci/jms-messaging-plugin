package com.redhat.jenkins.plugins.ci;

import com.redhat.jenkins.plugins.ci.messaging.FedMsgMessagingProvider;
import com.redhat.jenkins.plugins.ci.provider.data.*;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.Failure;
import hudson.util.Secret;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import com.redhat.jenkins.plugins.ci.authentication.activemq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.JMSMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.DefaultTopicProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.TopicProvider.TopicProviderDescriptor;

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
@Extension
public final class GlobalCIConfiguration extends GlobalConfiguration {

    public static final String DEFAULT_PROVIDER = "default";

    private transient String broker;
    private transient String topic;
    private transient String user;
    private transient Secret password;
    private transient boolean migrationInProgress = false;


    private List<JMSMessagingProvider> configs = new ArrayList<JMSMessagingProvider>();

    public boolean isMigrationInProgress() {
        return migrationInProgress;
    }

    private void setMigrationInProgress(boolean migrationInProgress) {
        this.migrationInProgress = migrationInProgress;
    }

    /**
     * The string in global configuration that indicates content is empty.
     */
    public static final String CONTENT_NONE = "-";

    private static final Logger log = Logger.getLogger(GlobalCIConfiguration.class.getName());

    public GlobalCIConfiguration(List<JMSMessagingProvider> configs) {
        this.configs = configs;
    }

    @DataBoundConstructor
    public GlobalCIConfiguration() {
        load();
    }

    protected Object readResolve() {
        if (broker != null) {
            log.info("Legacy Message Provider Broker value is not null.");
            if (configs.size() == 0) {
                log.info("Current Message Provider size is 0.");
                if (getProvider(DEFAULT_PROVIDER) == null) {
                    log.info("There is no default Message Provider.");
                    configs.add(new ActiveMqMessagingProvider(DEFAULT_PROVIDER,
                            broker, false, topic, new DefaultTopicProvider(), new UsernameAuthenticationMethod(user, password)));
                    log.info("Added default Message Provider using deprecated configuration.");
                    setMigrationInProgress(true);
                } else {
                    log.info("Default (" + DEFAULT_PROVIDER + ") Message Provider already exists.");
                }
            }
        }
        // Examine providers
        if (configs != null) {
            for (JMSMessagingProvider config : configs) {
                if (config instanceof ActiveMqMessagingProvider) {
                    ActiveMqMessagingProvider aconfig = (ActiveMqMessagingProvider) config;
                    if (aconfig.IsMigrationInProgress()) {
                        log.info("Migration in progress for ActiveMqMessagingProvider " + aconfig.getName());
                        setMigrationInProgress(true);
                    }
                }
            }
        }
        return this;
    }

    @DataBoundSetter
    public void setConfigs(List<JMSMessagingProvider> configs) {
        this.configs = configs;
    }

    public List<JMSMessagingProvider> getConfigs() {
        return Collections.unmodifiableList(this.configs);
    }

    public boolean addMessageProvider(JMSMessagingProvider provider) {
        if (configs == null) configs = new ArrayList<JMSMessagingProvider>();
        if (configs.contains(provider)) {
            throw new Failure("Attempt to add a duplicate message provider");
        }
        configs.add(provider);
        return true;
    }

    public JMSMessagingProvider getProvider(String name) {
        for (JMSMessagingProvider provider: getConfigs()) {
            if (provider.getName().equals(name)) {
                return provider;
            }
        }
        return null;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
        HashMap<String, String> names = new HashMap<>();
        Object obj = json.get("configs");
        if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray) obj;
            Iterator it = arr.iterator();
            while (it.hasNext()) {
                Object obj2 = it.next();
                JSONObject providerObj = (JSONObject) obj2;
                String name = providerObj.getString("name");
                if (names.containsKey(name)) {
                    throw new Failure("Attempt to add a duplicate JMS Message Provider - " + name);
                }
                names.put(name, name);
            }
        }
        req.bindJSON(this, json);
        save();
        return true;
    }

    @Override
    public String getDisplayName() {
        return Messages.PluginName();
    }

    public static @Nonnull GlobalCIConfiguration get() {
        ExtensionList<GlobalConfiguration> all = GlobalConfiguration.all();
        GlobalCIConfiguration c = all.get(GlobalCIConfiguration.class);
        if (c == null) {
            GlobalConfiguration registered = all.getDynamic(GlobalCIConfiguration.class.getCanonicalName());
            if (registered != null) {
                PluginManager pm = Jenkins.getInstance().pluginManager;
                PluginWrapper source = pm.whichPlugin(registered.getClass());
                throw new AssertionError("Version mismatch: GlobalCIConfiguration provided by other plugin: " + source);
            }
            throw new AssertionError("GlobalCIConfiguration is not registered: " + all);
        }
        return c;
    }

    // Jelly helper routines.
    public Boolean getFirstProviderOverrides() {
        if (configs != null && configs.size() > 0) {
            JMSMessagingProvider prov = configs.get(0);
            if (prov instanceof ActiveMqMessagingProvider && !(((ActiveMqMessagingProvider) prov).getTopicProvider() instanceof DefaultTopicProvider)) {
                return true;
            }
        }
        return false;
    }

    public String getFirstProviderName() {
        if (configs != null && configs.size() > 0) {
            JMSMessagingProvider prov = configs.get(0);
            return prov.getName();
        }
        return "";
    }

    public String getFirstProviderOverrideTopic(String type) {
        if (configs != null && configs.size() > 0) {
            JMSMessagingProvider prov = configs.get(0);
            if (prov instanceof ActiveMqMessagingProvider) {
                TopicProviderDescriptor tpd = (TopicProviderDescriptor) ((ActiveMqMessagingProvider) prov).getTopicProvider().getDescriptor();
                if (type.equals("subscriber")) {
                    return tpd.generateSubscriberTopic();
                } else if (type.equals("publisher")) {
                    return tpd.generatePublisherTopic();
                } else {
                    log.severe("Unknown topic provider type '" + type + "'.");
                    return "<unknown>";
                }
            }
        }
        return "";
    }

    public String getFirstProviderDisplayName() {
        if (configs != null && configs.size() > 0) {
            JMSMessagingProvider prov = configs.get(0);
            return prov.getDescriptor().getDisplayName();
        }
        return null;
    }

    public List<ProviderData> getSubscriberProviders() {
        List<ProviderData> pds = new ArrayList<>();
        if (configs != null) {
            for (JMSMessagingProvider p : getConfigs()) {
                if (p instanceof ActiveMqMessagingProvider) {
                    pds.add(new ActiveMQSubscriberProviderData(p.getName()));
                } else if (p instanceof FedMsgMessagingProvider) {
                    pds.add(new FedMsgSubscriberProviderData(p.getName()));
                } else {
                    pds.add(new RabbitMQSubscriberProviderData(p.getName()));
                }
            }
        }
        return pds;
    }

    public List<ProviderData> getPublisherProviders() {
        List<ProviderData> pds = new ArrayList<>();
        if (configs != null) {
            for (JMSMessagingProvider p : getConfigs()) {
                if (p instanceof ActiveMqMessagingProvider) {
                    pds.add(new ActiveMQPublisherProviderData(p.getName()));
                } else if (p instanceof FedMsgMessagingProvider) {
                    pds.add(new FedMsgPublisherProviderData(p.getName()));
                } else {
                    pds.add(new RabbitMQPublisherProviderData(p.getName()));
                }
            }
        }
        return pds;
    }
}
