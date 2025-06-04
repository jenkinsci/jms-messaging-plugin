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
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.jenkins.plugins.ci.authentication.AuthenticationMethod;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

public class KafkaMessagingProvider extends JMSMessagingProvider {

    private static final long serialVersionUID = -5806556233319068296L;

    private String topic;
    private String producerProperties;
    private String consumerProperties;

    @DataBoundConstructor
    public KafkaMessagingProvider(String name, String topic, String producerProperties, String consumerProperties) {
        this.name = name;
        this.topic = topic;
        this.producerProperties = producerProperties;
        this.consumerProperties = consumerProperties;
        log.fine(String.format("provider: [%s], topic: [%s], connection properties: [%s]", name, topic,
                producerProperties, consumerProperties));

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
        return getMergedProducerProperties(producerProperties);
    }

    public Properties getMergedConsumerProperties() {
        return getMergedConsumerProperties(consumerProperties);
    }

    static private Properties getMergedProducerProperties(String properties) {
        return getMergedProperties(getDefaultProducerProperties(), properties);
    }

    static private Properties getMergedConsumerProperties(String properties) {
        return getMergedProperties(getDefaultConsumerProperties(), properties);
    }

    static private Properties getMergedProperties(Properties defaults, String properties) {
        try {
            defaults.load(IOUtils.toInputStream(properties == null ? "" : properties, Charset.defaultCharset()));
        } catch (IOException e) {
            log.log(Level.WARNING, String.format("bad properties: %s", properties));
        }
        return defaults;
    }

    static private Properties getDefaultProducerProperties() {
        Properties props = new Properties();
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 0);
        return props;
    }

    static private Properties getDefaultConsumerProperties() {
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
    public static class KafkaMessagingProviderDescriptor extends MessagingProviderDescriptor {
        @Override
        public String getDisplayName() {
            return "Kafka";
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("topic") String topic,
                @QueryParameter("producerProperties") String producerProperties,
                @QueryParameter("consumerProperties") String consumerProperties) {

            AuthenticationMethod.checkAdmin();

            Properties pprops = getMergedProducerProperties(producerProperties);
            Properties cprops = getMergedConsumerProperties(consumerProperties);

            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(null);
            try (KafkaConsumer consumer = new KafkaConsumer<>(cprops);
                    KafkaProducer producer = new KafkaProducer<>(pprops)) {

                // Test producer.
                ProducerRecord<String, String> record = new ProducerRecord<>("test-topic", "test-key", "test-value");
                producer.send(record).get();

                // Test consumer.
                consumer.subscribe(Collections.singletonList(topic));
                consumer.poll(Duration.ofMillis(100));

                return FormValidation.ok(Messages.SuccessBrokersConnect(pprops.get("bootstrap.servers"),
                        cprops.get("bootstrap.servers")));
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in KafkaMessagingProvider.doTestConnection: ", e);
                return FormValidation.error(Messages.Error() + ": " + e);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

    }
}
