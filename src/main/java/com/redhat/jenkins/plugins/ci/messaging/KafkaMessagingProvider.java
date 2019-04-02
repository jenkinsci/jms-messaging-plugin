package com.redhat.jenkins.plugins.ci.messaging;

import com.redhat.jenkins.plugins.ci.messaging.data.KafkaMessage;
import com.redhat.jenkins.plugins.ci.provider.data.KafkaProviderData;
import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import com.redhat.jenkins.plugins.ci.messaging.data.FedmsgMessage;
import com.redhat.jenkins.plugins.ci.provider.data.FedMsgProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.logging.Level;

/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 * Copyright (c) Valentin Titov
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
public class KafkaMessagingProvider extends JMSMessagingProvider {

    private static final long serialVersionUID = -5806556233319068296L;

    private String topic;
    private String connectionProperties;

    @DataBoundConstructor
    public KafkaMessagingProvider(String name,
                                  String topic,
                                  String connectionProperties) {
        this.name = name;
        this.topic = topic;
        this.connectionProperties = connectionProperties;
        log.fine(String.format("provider: [%s], topic: [%s], connection properties: [%s]", name, topic, connectionProperties));

    }

    public String getTopic() {
        return topic;
    }

    public String getConnectionProperties() {
        return connectionProperties;
    }

    Properties getConnectionPropertiesProperties() {
        try {
            Properties props = new Properties();
            props.put("max.poll.records", 1);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            props.put("enable.auto.commit", "true");
            props.put("group.id", "jctl");
            props.put("auto.commit.interval.ms", "1000");
            props.put("session.timeout.ms", "30000");
            props.put("acks", "all");
            props.put("retries", 0);
            //props.put("linger.ms", 1);

            props.load(IOUtils.toInputStream(connectionProperties == null  ? "" : connectionProperties, Charset.defaultCharset()));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            props.storeToXML(output, null);
            String string = new String(output.toByteArray());
            log.fine(String.format("connection properties [%s][%s][%s]", name, topic, string));
            return props;
        } catch (IOException e) {
            log.log(Level.WARNING, String.format("bad connection properties: %s", connectionProperties));
        }
        return null;
    }

    public String formatMessage(KafkaMessage data) {
        return data.getBody();
    }

    @Override
    public JMSMessagingWorker createWorker(ProviderData pdata, String jobname) {
        return new KafkaMessagingWorker(this, ((KafkaProviderData)pdata).getOverrides(), jobname);
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
            return "Kafka Messaging";
        }

    }

}
