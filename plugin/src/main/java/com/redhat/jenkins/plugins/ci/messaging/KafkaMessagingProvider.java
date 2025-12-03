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
package com.redhat.jenkins.plugins.ci.messaging;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.redhat.jenkins.plugins.ci.authentication.kafka.KafkaAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.authentication.kafka.KafkaAuthenticationMethod.AuthenticationMethodDescriptor;
import com.redhat.jenkins.plugins.ci.authentication.kafka.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.CredentialLookup;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public class KafkaMessagingProvider extends JMSMessagingProvider {

    private static final long serialVersionUID = -5806556233319068296L;

    private String topic;
    private String producerProperties;
    private String consumerProperties;
    private KafkaAuthenticationMethod authenticationMethod;

    @DataBoundConstructor
    public KafkaMessagingProvider(String name, String topic, String producerProperties, String consumerProperties,
            KafkaAuthenticationMethod authenticationMethod) {
        this.name = name;
        this.topic = topic;
        this.producerProperties = producerProperties;
        this.consumerProperties = consumerProperties;
        this.authenticationMethod = authenticationMethod;
    }

    public void setName(String name) {
        this.name = name;
    }

    @DataBoundSetter
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @DataBoundSetter
    public void setProducerProperties(String producerProperties) {
        this.producerProperties = producerProperties;
    }

    @DataBoundSetter
    public void setConsumerProperties(String consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    @DataBoundSetter
    public void setAuthentication(KafkaAuthenticationMethod authenticationMethod) {
        this.authenticationMethod = authenticationMethod;
    }

    public String getTopic() {
        return topic;
    }

    public String getProducerProperties() {
        return producerProperties;
    }

    public String getConsumerProperties() {
        return consumerProperties;
    }

    public Properties getMergedProducerProperties() {
        return getMergedProperties(getDefaultProducerProperties(), producerProperties);
    }

    public Properties getMergedConsumerProperties() {
        return getMergedProperties(getDefaultConsumerProperties(), consumerProperties);
    }

    public KafkaAuthenticationMethod getAuthenticationMethod() {
        return authenticationMethod;
    }

    private Properties getMergedProperties(Properties defaults, String properties) {
        Properties props = new Properties();
        try {
            props.load(IOUtils.toInputStream(properties == null ? "" : properties, Charset.defaultCharset()));
        } catch (IOException e) {
            log.log(Level.WARNING, String.format("bad properties: %s", properties));
        }
        props.putAll(defaults);

        HashMap<String, String> values = new HashMap<>();
        if (authenticationMethod instanceof UsernameAuthenticationMethod) {
            UsernameAuthenticationMethod uam = (UsernameAuthenticationMethod) authenticationMethod;
            String credentialId = uam.getCredentialId();
            StandardUsernamePasswordCredentials credentials = CredentialLookup.lookupById(credentialId,
                    StandardUsernamePasswordCredentials.class);

            if (credentials == null) {
                log.warning(String.format("Credential '%s' not found", credentialId));
            } else {
                values.put("USERNAME", credentials.getUsername());
                values.put("PASSWORD", credentials.getPassword().getPlainText());
            }
        }

        StringSubstitutor sub = new StringSubstitutor(values);
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = (String) entry.getKey();
            if (entry.getValue() instanceof String) {
                String value = (String) entry.getValue();
                props.put(key, sub.replace(value));
            }
        }

        return props;
    }

    private Properties getDefaultProducerProperties() {
        Properties props = new Properties();
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 0);
        return props;
    }

    private Properties getDefaultConsumerProperties() {
        Properties props = new Properties();
        props.put("max.poll.records", 1);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("group.id", "jms-messaging-" + UUID.randomUUID());
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "30000");
        return props;
    }

    @Override
    public JMSMessagingWorker createWorker(ProviderData pdata, String jobname) {
        return new KafkaMessagingWorker(this, (KafkaProviderData) pdata, jobname);
    }

    @Override
    public JMSMessageWatcher createWatcher(String jobname) {
        return new KafkaMessageWatcher(jobname);
    }

    @Override
    public Descriptor<JMSMessagingProvider> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(KafkaMessagingProviderDescriptor.class);
    }

    @Extension
    @Symbol("kafka")
    public static class KafkaMessagingProviderDescriptor extends MessagingProviderDescriptor {
        @Override
        public String getDisplayName() {
            return "Kafka";
        }

        public ExtensionList<AuthenticationMethodDescriptor> getAuthenticationMethodDescriptors() {
            return AuthenticationMethodDescriptor.all();
        }
    }
}
