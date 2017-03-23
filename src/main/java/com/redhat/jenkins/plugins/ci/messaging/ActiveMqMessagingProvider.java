package com.redhat.jenkins.plugins.ci.messaging;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.util.Secret;

import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.redhat.jenkins.plugins.ci.authentication.AuthenticationMethod.AuthenticationMethodDescriptor;
import com.redhat.jenkins.plugins.ci.authentication.activemq.ActiveMQAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.authentication.activemq.UsernameAuthenticationMethod;

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
public class ActiveMqMessagingProvider extends JMSMessagingProvider {

    private String broker;
    private String topic;
    private transient String user;
    private transient Secret password;
    private transient boolean migrationInProgress = false;
    private ActiveMQAuthenticationMethod authenticationMethod;
    private transient static final Logger log = Logger.getLogger(ActiveMqMessagingProvider.class.getName());

    @DataBoundConstructor
    public ActiveMqMessagingProvider(String name, String broker, String topic, ActiveMQAuthenticationMethod authenticationMethod) {
        this.name = name;
        this.broker = broker;
        this.topic = topic;
        this.authenticationMethod = authenticationMethod;
    }

    protected Object readResolve() {
        if (user != null) {
            log.info("Legacy Message Provider username value is not null.");
            authenticationMethod = new UsernameAuthenticationMethod(user, password);
            log.info("Added default username/password authentication method using deprecated configuration.");
            setMigrationInProgress(true);
        }
        return this;
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
    public void setAuthenticationMethod(ActiveMQAuthenticationMethod method) {
        this.authenticationMethod = method;
    }

    @Override
    public Descriptor<JMSMessagingProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(ActiveMqMessagingProviderDescriptor.class);
    }

    public String getBroker() {
        return broker;
    }

    public String getTopic() {
        return topic;
    }

    public ActiveMQAuthenticationMethod getAuthenticationMethod() {
        return authenticationMethod;
    }

    public ActiveMQConnectionFactory getConnectionFactory() {
        return getConnectionFactory(getBroker(), getAuthenticationMethod());
    }

    public ActiveMQConnectionFactory getConnectionFactory(String broker, ActiveMQAuthenticationMethod authenticationMethod) {
        return authenticationMethod.getConnectionFactory(broker);
    }

    @Override
    public JMSMessagingWorker createWorker(MessagingProviderOverrides overrides, String jobname) {
        return new ActiveMqMessagingWorker(this, overrides, jobname);
    }

    public boolean IsMigrationInProgress() {
        return migrationInProgress;
    }

    private void setMigrationInProgress(boolean migrationInProgress) {
        this.migrationInProgress = migrationInProgress;
    }

    @Extension
    public static class ActiveMqMessagingProviderDescriptor extends MessagingProviderDescriptor {
        private final Logger log = Logger.getLogger(ActiveMqMessagingProviderDescriptor.class.getName());

        @Override
        public String getDisplayName() {
            return "Active MQ";
        }

        public ExtensionList<AuthenticationMethodDescriptor> getAuthenticationMethodDescriptors() {
            return AuthenticationMethodDescriptor.all();
        }
    }
}
