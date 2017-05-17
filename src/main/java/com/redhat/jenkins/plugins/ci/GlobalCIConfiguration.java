package com.redhat.jenkins.plugins.ci;

import hudson.Extension;
import hudson.model.Failure;
import hudson.util.Secret;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

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
 */@Extension
public final class GlobalCIConfiguration extends GlobalConfiguration {

    private transient String broker;
    private transient String topic;
    private transient String user;
    private transient Secret password;
    private transient boolean migrationInProgress = false;

    private static final String PLUGIN_NAME = Messages.PluginName();

    public static final GlobalCIConfiguration EMPTY_CONFIG =
            new GlobalCIConfiguration(Collections.<JMSMessagingProvider>emptyList());
    private List<JMSMessagingProvider> configs = new ArrayList<JMSMessagingProvider>();
    public static final String DEFAULT_PROVIDER = "default";

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

    public GlobalCIConfiguration() {
        load();
    }

    protected Object readResolve() {
        if (broker != null) {
            log.info("Legacy Message Provider Broker value is not null.");
            if (configs.size() == 0) {
                log.info("Current Message Provider size is 0.");
                if (getProvider(DEFAULT_PROVIDER)==null) {
                    log.info("There is no default Message Provider.");
                    configs.add(new ActiveMqMessagingProvider(DEFAULT_PROVIDER,
                            broker, topic, new DefaultTopicProvider(), new UsernameAuthenticationMethod(user, password)));
                    log.info("Added default Message Provider using deprecated configuration.");
                    setMigrationInProgress(true);
                } else {
                    log.info("Default (" + DEFAULT_PROVIDER + ") Message Provider already exists.");
                }
            }
        }
        // Examine providers
        if (configs != null) {
            for (JMSMessagingProvider config: configs) {
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
        return PLUGIN_NAME;
    }


    public static GlobalCIConfiguration get() {
        try {
            return (GlobalConfiguration.all() == null ? null : GlobalConfiguration.all().get(GlobalCIConfiguration.class));
        } catch (Exception e) {
            log.warning("Unable to get global configuration.");
        }
        return null;
    }

    // Jelly helper routines.
    public Boolean getFirstProviderOverrides() {
        return getFirstProviderOverrideTopic().length() > 0;
    }

    public String getFirstProviderOverrideTopic() {
        if (configs != null && configs.size() > 0) {
            JMSMessagingProvider prov = configs.get(0);
            if (prov instanceof ActiveMqMessagingProvider) {
                TopicProviderDescriptor tpd = (TopicProviderDescriptor) ((ActiveMqMessagingProvider) prov).getTopicProvider().getDescriptor();
                return tpd.generateSubscriberTopic();
            }
        }
        return "";
    }
}
