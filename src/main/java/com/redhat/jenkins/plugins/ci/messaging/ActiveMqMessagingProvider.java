package com.redhat.jenkins.plugins.ci.messaging;

import com.redhat.jenkins.plugins.ci.CIBuildTrigger;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.utils.MessageUtils;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.Topic;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.Connection;
import javax.jms.TopicSubscriber;
import javax.security.auth.login.LoginException;
import javax.servlet.ServletException;

import static com.redhat.jenkins.plugins.ci.CIBuildTrigger.findTrigger;

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
public class ActiveMqMessagingProvider extends MessagingProvider {

    private String broker;
    private String topic;
    private String user;
    private Secret password;
    private transient static final Logger log = Logger.getLogger(ActiveMqMessagingProvider.class.getName());

    private transient Connection connection;
    private transient TopicSubscriber subscriber;
    private transient static final Integer RETRY_MINUTES = 1;


    @DataBoundConstructor
    public ActiveMqMessagingProvider(String name, String broker, String topic, String user,
                                     Secret password) {
        this.name = name;
        this.broker = broker;
        this.topic = topic;
        this.user = user;
        this.password = password;
    }

    @DataBoundSetter
    public void setBroker(String broker) {
        this.broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
    }

    @DataBoundSetter
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    @DataBoundSetter
    public void setPassword(Secret password) {
        this.password = password;
    }

    @DataBoundSetter
    public void setPassword(String password) {
        this.password = Secret.fromString(password);
    }

    @Override
    public Descriptor<MessagingProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(ActiveMqMessagingProviderDescriptor.class);
    }

    public String getBroker() {
        return broker;
    }

    public String getTopic() {
        return topic;
    }

    public String getUser() {
        return user;
    }

    public Secret getPassword() {
        return password;
    }

    @Override
    public MessagingWorker createWorker(String jobname) {
        return new ActiveMqMessagingWorker(this, jobname);
    }

    @Extension
    public static class ActiveMqMessagingProviderDescriptor extends MessagingProviderDescriptor {
        private final Logger log = Logger.getLogger(ActiveMqMessagingProviderDescriptor.class.getName());

        @Override
        public String getDisplayName() {
            return "Active MQ";
        }

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

    }
}
