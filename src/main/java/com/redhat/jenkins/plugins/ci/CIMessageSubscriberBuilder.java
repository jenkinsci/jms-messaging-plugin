package com.redhat.jenkins.plugins.ci;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;

import net.sf.json.JSONObject;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.redhat.jenkins.plugins.Messages;
import com.redhat.utils.MessageUtils;

public class CIMessageSubscriberBuilder extends Builder {
    private static final Logger log = Logger.getLogger(CIMessageSubscriberBuilder.class.getName());

    private static final String BUILDER_NAME = Messages.SubscriberBuilder();

    public static final Integer DEFAULT_TIMEOUT_IN_MINUTES = 60;

    private String selector;
    private String variable;
    private Integer timeout;

    @DataBoundConstructor
    public CIMessageSubscriberBuilder(String selector, String variable, Integer timeout) {
        this.selector = selector;
        this.variable = variable;
        this.timeout = timeout;
    }

    public CIMessageSubscriberBuilder(String selector, Integer timeout) {
        this.selector = selector;
        this.timeout = timeout;
    }

    @DataBoundSetter
    public void setVariable(String variable) {
        this.variable = variable;
    }

    @DataBoundSetter
    public void setSelector(String selector) {
        this.selector = selector;
    }

    @DataBoundSetter
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getSelector() {
        return selector;
    }

    public String getVariable() {
        return variable;
    }

    public Integer getTimeout() {
        return timeout;
    }


    public String waitforCIMessage(Run<?, ?> build, Launcher launcher, TaskListener listener) {
        String ip = null;
        try {
            ip = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.severe("Unable to get localhost IP address.");
        }

        GlobalCIConfiguration config = GlobalCIConfiguration.get();
        if (config != null) {
            String user = config.getUser();
            String password = config.getPassword().getPlainText();
            String broker = config.getBroker();
            String topic = config.getTopic();

            if (ip != null && user != null && password != null && topic != null && broker != null) {
                log.info("Waiting for message with selector: " + selector);
                Connection connection = null;
                MessageConsumer consumer = null;
                try {
                    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(user, password, broker);
                    connection = connectionFactory.createConnection();
                    connection.setClientID(ip + "_" + UUID.randomUUID().toString());
                    connection.start();
                    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    Topic destination = session.createTopic(topic);

                    consumer = session.createConsumer(destination, selector);

                    Message message = consumer.receive(timeout*60*1000);
                    if (message != null) {
                        String value = MessageUtils.getMessageBody(message);
                        if (StringUtils.isNotEmpty(variable)) {
                            EnvVars vars = new EnvVars();
                            vars.put(variable, value);
                            build.addAction(new CIEnvironmentContributingAction(vars));
                        }
                        return value;
                    }
                    log.info("Timed out waiting for message!");
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
                } finally {
                    if (consumer != null) {
                        try {
                            consumer.close();
                        } catch (Exception e) {
                        }
                    }
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (Exception e) {
                        }
                    }
                }
            } else {
                log.severe("One or more of the following is invalid (null): ip, user, password, topic, broker.");
            }
        }
        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (waitforCIMessage(build, launcher, listener)== null) {
            return false;
        }
        return true;
    }

    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {

        public String getDisplayName() {
            return BUILDER_NAME;
        }

        public Integer getDefaultTimeout() {
            return DEFAULT_TIMEOUT_IN_MINUTES;
        }

        @Override
        public CIMessageSubscriberBuilder newInstance(StaplerRequest sr, JSONObject jo) {
            int timeout = getDefaultTimeout();
            if (jo.getString("timeout") != null && !jo.getString("timeout").isEmpty()) {
                timeout = jo.getInt("timeout");
            }
            return new CIMessageSubscriberBuilder(jo.getString("selector"), jo.getString("variable"), timeout);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest sr, JSONObject formData) throws FormException {
            save();
            return super.configure(sr, formData);
        }

        public FormValidation doCheckSelector(
                @QueryParameter String selector) {
            if (selector == null || selector.isEmpty()) {
                return FormValidation.error("Please enter a JMS selector.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVariable(
                @QueryParameter String variable) {
            if (variable == null || variable.isEmpty()) {
                return FormValidation.error("Please enter a variable name to hold the received message result.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTimeout(
                @QueryParameter String timeout) {
            try {
                if (timeout == null || timeout.isEmpty() || Integer.parseInt(timeout) <= 0) {
                    return FormValidation.error("Please enter a positive timeout value.");
                }
            } catch (NumberFormatException e) {
                return FormValidation.error("Please enter a valid timeout value.");
            }
            return FormValidation.ok();
        }
    }
}
