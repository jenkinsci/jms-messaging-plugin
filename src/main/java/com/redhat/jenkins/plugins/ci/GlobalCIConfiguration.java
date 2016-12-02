package com.redhat.jenkins.plugins.ci;

import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.MessagingProvider;
import hudson.Extension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

@Extension
public final class GlobalCIConfiguration extends GlobalConfiguration {

    private transient String broker;
    private transient String topic;
    private transient String user;
    private transient Secret password;
    private transient boolean migrationInProgress = false;

    private static final String PLUGIN_NAME = Messages.PluginName();

    public static final GlobalCIConfiguration EMPTY_CONFIG =
            new GlobalCIConfiguration(Collections.<MessagingProvider>emptyList());
    private List<MessagingProvider> configs = new ArrayList<MessagingProvider>();
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

    public GlobalCIConfiguration(List<MessagingProvider> configs) {
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
                            broker, topic, user, password));
                    log.info("Added default Message Provider using deprecated configuration.");
                    setMigrationInProgress(true);
                    save();
                } else {
                    log.info("Default (" + DEFAULT_PROVIDER + ") Message Provider already exists.");
                }
            }
        }
        return this;
    }

    @SuppressWarnings("unused")
    @DataBoundSetter
    public void setConfigs(List<MessagingProvider> configs) {
        this.configs = configs;
    }

    public List<MessagingProvider> getConfigs() {
        return configs;
    }

    public MessagingProvider getProvider(String name) {
        for (MessagingProvider provider: configs) {
            if (provider.getName().equals(name)) {
                return provider;
            }
        }
        return null;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
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

}
