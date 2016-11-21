package com.redhat.jenkins.plugins.ci;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.Session;
import javax.security.auth.login.LoginException;
import javax.servlet.ServletException;

import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.redhat.jenkins.plugins.Messages;

@Extension
public final class GlobalCIConfiguration extends GlobalConfiguration {

    private static final String PLUGIN_NAME = Messages.PluginName();
    /**
     * The string in global configuration that indicates content is empty.
     */
    public static final String CONTENT_NONE = "-";

    private static final Logger log = Logger.getLogger(GlobalCIConfiguration.class.getName());

    private String broker;
    private String topic = "CI";
    private String user;
    private Secret password;

    @DataBoundConstructor
    public GlobalCIConfiguration(String broker, String topic, String user, Secret password) {
        this.broker = broker;
        this.topic = topic;
        this.user = user;
        this.password = password;
    }

    /**
     * Create GlobalCIConfiguration from disk.
     */
    public GlobalCIConfiguration() {
        load();
    }

    @Override
    public String getDisplayName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
        req.bindJSON(this, json);

        try {
	        if (isValidURL(broker) && testConnection(broker, topic, user, password)) {
	            save();
//	            for (String fullname : CIBuildTrigger.triggerInfo.keySet()) {
//	                CIBuildTrigger trigger = CIBuildTrigger.findTrigger(fullname);
//	                if (trigger != null) {
//	                    trigger.connect();
//	                } else {
//	                    log.warning("Unable to find CIBuildTrigger for '" + fullname + "'.");
//	                }
//	            }
	            return true;
	        }
        } catch (Exception e) {
	        log.log(Level.SEVERE, "EXCEPTION IN CONFIGURE, returning false!", e);
        }
        return false;
    }
    public String getBroker() {
        return broker;
    }

    public void setBroker(final String broker) {
        this.broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Secret getPassword() {
        return password;
    }

    public void setPassword(Secret password) {
        this.password = password;
    }

    public void setPassword(String password) {
        this.password = Secret.fromString(password);
    }

//    public FormValidation doCheckUri(@QueryParameter("uri") String uri,
//    		@QueryParameter("exchange") String exchange,
//            @QueryParameter("user") String user,
//            @QueryParameter("password") Secret password) throws ServletException {
//    	return doTestConnection(uri, exchange, user, password);
//    }

    public FormValidation doTestConnection(@QueryParameter("broker") String broker,
    		@QueryParameter("topic") String topic,
            @QueryParameter("user") String user,
            @QueryParameter("password") Secret password) throws ServletException {
        broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
        if (broker != null && isValidURL(broker)) {
    		try {
    			if (testConnection(broker, topic, user, password)) {
    				return FormValidation.ok(Messages.SuccessBrokerConnect(broker));
    			}
    		} catch (LoginException e) {
                return FormValidation.error(Messages.AuthFailure());
    		} catch (Exception e) {
    			log.log(Level.SEVERE, "Unhandled exception in doTestConnection: ", e);
                return FormValidation.error(Messages.Error() + ": " + e);
			}
        }
        return FormValidation.error(Messages.InvalidURI());
    }

    public static GlobalCIConfiguration get() {
        try {
            return (GlobalConfiguration.all() == null ? null : GlobalConfiguration.all().get(GlobalCIConfiguration.class));
        } catch (Exception e) {
            log.warning("Unable to get global configuration.");
        }
        return null;
    }

    private boolean testConnection(String broker, String topic, String user, Secret password) throws Exception {
        broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
        if (broker != null && isValidURL(broker)) {
			ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(user, password.getPlainText(), broker);
			Connection connection = connectionFactory.createConnection();
			connection.start();
        	Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        	session.close();
        	connection.close();
          	return true;
        }
        return false;

    }

    private static boolean isValidURL(String url) {
        try {
            new URI(url);
        } catch (URISyntaxException e) {
			log.log(Level.SEVERE, "URISyntaxException, returning false.");
            return false;
        }
        return true;
    }
}
