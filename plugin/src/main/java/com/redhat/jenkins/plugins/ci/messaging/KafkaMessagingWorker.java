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
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.redhat.jenkins.plugins.ci.CIEnvironmentContributingAction;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.*;
import com.redhat.utils.PluginUtils;

import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class KafkaMessagingWorker extends JMSMessagingWorker {
    private static final Logger log = Logger.getLogger(KafkaMessagingWorker.class.getName());
    private final KafkaMessagingProvider provider;
    private final KafkaProviderData pdata;
    static final String DEFAULT_TOPIC = "io.jenkins";
    KafkaConsumer<String, String> consumer;

    public KafkaMessagingWorker(JMSMessagingProvider messagingProvider, KafkaProviderData pdata, String jobname) {
        super(messagingProvider, pdata.getOverrides(), jobname);
        this.provider = (KafkaMessagingProvider) messagingProvider;
        this.pdata = pdata;
    }

    @Override
    public boolean subscribe(String jobname, String selector) {
        this.topic = getTopic(provider);
        log.info(String.format("subscribe job %s to topic [%s]", jobname, this.topic));
        if (this.topic != null) {
            while (!Thread.currentThread().isInterrupted() && connect()) {
                try {
                    log.info("Subscribing job '" + jobname + "' to " + this.topic + " topic.");
                    consumer.subscribe(Collections.singletonList(this.topic));
                    log.info("Subscribed job '" + jobname + "' to topic '" + this.topic + "'.");

                    return true;
                } catch (Exception ex) {

                    // Either we were interrupted, or something else went
                    // wrong. If we were interrupted, then we will jump ship
                    // on the next iteration. If something else happened,
                    // then we just unsubscribe here, sleep, so that we may
                    // try again on the next iteration.

                    log.log(Level.SEVERE, "Exception raised while subscribing job '" + jobname + "', retrying in "
                            + RETRY_MINUTES + " minutes.", ex);
                    if (!Thread.currentThread().isInterrupted()) {

                        unsubscribe(jobname);

                        try {
                            Thread.sleep(TimeUnit.MINUTES.toMillis(RETRY_MINUTES));
                        } catch (InterruptedException ie) {
                            // We were interrupted while waiting to retry.
                            // We will jump ship on the next iteration.

                            // NB: The interrupt flag was cleared when
                            // InterruptedException was thrown. We have to
                            // re-install it to make sure we eventually
                            // leave this thread.
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void unsubscribe(String jobname) {
        disconnect();
    }

    @Override
    public void receive(String jobname, ProviderData pdata) {
        KafkaSubscriberProviderData pd = (KafkaSubscriberProviderData) pdata;
        while (!subscribe(jobname) && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                // We were interrupted while waiting to retry. We will
                // jump ship on the next iteration.

                // NB: The interrupt flag was cleared when
                // InterruptedException was thrown. We have to
                // re-install it to make sure we eventually leave this
                // thread.
                Thread.currentThread().interrupt();
            }
        }

        if (!Thread.currentThread().isInterrupted()) {
            int timeout = (pd.getTimeout() != null ? pd.getTimeout()
                    : KafkaSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES);
            try {
                log.info("Job '" + jobname + "' waiting to receive message");
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMinutes(timeout));
                Iterator<ConsumerRecord<String, String>> it = records.iterator();
                if (it.hasNext()) {
                    ConsumerRecord<String, String> rec = it.next();
                    log.info(String.format("kafka message received [%s]", rec.toString()));
                    log.info(String.format("kafka message received from [%s] [%s] [%s]", rec.topic(), rec.key(),
                            rec.value()));
                    if (provider.verify(rec.value(), pd.getChecks(), jobname)) {
                        Map<String, String> params = new HashMap<String, String>();
                        if (StringUtils.isNotEmpty(pd.getMessageVariable())) {
                            if (rec.value() != null) {
                                params.put(pd.getMessageVariable(), rec.value());
                            }
                            params.put(pd.getRecordVariable(), consumerRecordToJson(rec));
                        }
                        trigger(jobname, rec.value(), params);
                    }
                } else {
                    log.info("No message received for the past " + timeout + " minutes, re-subscribing for job '"
                            + jobname + "'.");
                    unsubscribe(jobname);
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    // Something other than an interrupt causes this.
                    // Unsubscribe, but stay in our loop and try to reconnect..
                    log.log(Level.WARNING, "JMS exception raised, going to re-subscribe for job '" + jobname + "'.", e);
                    unsubscribe(jobname); // Try again next time.
                }
            }
        } else {
            // We are about to leave the loop, so unsubscribe.
            unsubscribe(jobname);
        }
    }

    @Override
    public boolean connect() {
        if (consumer == null) {
            // This is a fix for org.apache.kafka.common.config.ConfigException:
            // Invalid value org.apache.kafka.common.serialization.StringDeserializer for configuration
            // value.deserializer: Class org.apache.kafka.common.serialization.StringDeserializer could not be found.
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(null);
            consumer = new KafkaConsumer<>(pdata.mergeProperties(provider.getMergedConsumerProperties()));
            Thread.currentThread().setContextClassLoader(original);
        }
        return true;
    }

    @Override
    public void disconnect() {
        boolean resetInterrupt = Thread.currentThread().isInterrupted();
        try {
            if (consumer != null) {
                log.info("Unsubscribing job '" + jobname + "' from " + this.topic + " topic.");
                consumer.close();
            }
        } catch (Exception e) {
            log.warning(e.getMessage());
        } finally {
            consumer = null;
        }

        if (resetInterrupt && !Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public SendResult sendMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        KafkaPublisherProviderData pd = (KafkaPublisherProviderData) pdata;
        String body = "";
        String msgId = "";
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(
                pd.mergeProperties(provider.getMergedProducerProperties()))) {
            EnvVars env = new EnvVars();
            env.putAll(build.getEnvironment(listener));
            env.put("CI_NAME", build.getParent().getName());
            if (!build.isBuilding()) {
                String ciStatus = (build.getResult() == Result.SUCCESS ? "passed" : "failed");
                env.put("CI_STATUS", ciStatus);
                env.put("BUILD_STATUS", Objects.requireNonNull(build.getResult()).toString());
            }

            String ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener));
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(ltopic, null,
                    Instant.now().toEpochMilli(), UUID.randomUUID().toString(),
                    PluginUtils.getSubstitutedValue(pd.getMessageContent(), env));
            body = producerRecord.value();
            msgId = producerRecord.key();

            producer.send(producerRecord).get();
            log.info(String.format("message id: %s body: %s", producerRecord.key(), producerRecord.value()));
            listener.getLogger()
                    .println(String.format("message id: %s body: %s", producerRecord.key(), producerRecord.value()));

        } catch (ExecutionException | IOException | InterruptedException e) {
            if (pd.isFailOnError()) {
                log.severe("Unhandled exception in perform: ");
                log.severe(ExceptionUtils.getStackTrace(e));
                listener.fatalError("Unhandled exception in perform: ");
                listener.fatalError(ExceptionUtils.getStackTrace(e));
                return new SendResult(false, msgId, body);
            } else {
                log.warning("Unhandled exception in perform: ");
                log.warning(ExceptionUtils.getStackTrace(e));
                listener.error("Unhandled exception in perform: ");
                listener.error(ExceptionUtils.getStackTrace(e));
                return new SendResult(true, msgId, body);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
        return new SendResult(true, msgId, body);
    }

    @Override
    public String waitForMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        KafkaSubscriberProviderData pd = (KafkaSubscriberProviderData) pdata;

        String ltopic = getTopic(provider);
        try {
            ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener));
        } catch (IOException | InterruptedException e) {
            log.warning(e.getMessage());
        }

        try (KafkaConsumer<String, String> lconsumer = new KafkaConsumer<>(
                pd.mergeProperties(provider.getMergedConsumerProperties()))) {
            int timeout = (pd.getTimeout() != null ? pd.getTimeout()
                    : KafkaSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES);
            lconsumer.subscribe(Collections.singletonList(ltopic));
            log.info("Job '" + jobname + "' waiting to receive message");
            listener.getLogger().println("Job '" + jobname + "' waiting to receive message");
            ConsumerRecords<String, String> records = lconsumer.poll(Duration.ofMinutes(timeout));
            Iterator<ConsumerRecord<String, String>> it = records.iterator();
            if (it.hasNext()) {
                ConsumerRecord<String, String> rec = it.next();
                listener.getLogger().println(String.format("Received a message: %s", rec.toString()));
                // log.info(String.format("kafka message received [%s]", rec.toString()));
                // log.info(String.format("kafka message received from [%s] [%s] [%s]", rec.topic(), rec.key(),
                // rec.value()));
                if (provider.verify(rec.value(), pd.getChecks(), jobname)) {
                    if (StringUtils.isNotEmpty(pd.getMessageVariable())) {
                        EnvVars vars = new EnvVars();
                        if (rec.value() != null) {
                            vars.put(pd.getMessageVariable(), rec.value());
                        }
                        vars.put(pd.getRecordVariable(), consumerRecordToJson(rec));
                        build.addAction(new CIEnvironmentContributingAction(vars));
                    }
                    return rec.value();
                }
            } else {
                log.warning("Timed out waiting for message!");
                listener.getLogger().println("Timed out waiting for message!");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
        }
        return null;
    }

    @Override
    public String getDefaultTopic() {
        return DEFAULT_TOPIC;
    }

    private String consumerRecordToJson(ConsumerRecord<String, String> rec) {
        // Convert the consumer record to a JSON string, excluding value.
        ConsumerRecord<String, String> copy = new ConsumerRecord<>(rec.topic(), rec.partition(), rec.offset(),
                rec.timestamp(), rec.timestampType(), rec.serializedKeySize(), rec.serializedValueSize(), rec.key(),
                null, rec.headers(), rec.leaderEpoch());
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        mapper.registerModule(new Jdk8Module());
        try {
            return mapper.writeValueAsString(copy);
        } catch (IOException e) {
            log.severe("Unable to convert consumer record to JSON");
            e.printStackTrace();
        }
        return "";
    }
}
