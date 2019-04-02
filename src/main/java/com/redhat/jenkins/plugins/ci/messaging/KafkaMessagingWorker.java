package com.redhat.jenkins.plugins.ci.messaging;

import com.redhat.jenkins.plugins.ci.messaging.data.KafkaMessage;
import com.redhat.jenkins.plugins.ci.provider.data.*;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.val;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.jenkins.plugins.ci.CIEnvironmentContributingAction;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.utils.PluginUtils;

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
public class KafkaMessagingWorker extends JMSMessagingWorker {

    private static final Logger log = Logger.getLogger(KafkaMessagingWorker.class.getName());

    private final KafkaMessagingProvider provider;

    static final String DEFAULT_TOPIC = "io.jenkins"; // FIXME

    private boolean interrupt = false; // TODO do we need interrupt for kafka?

    KafkaConsumer<String, String> consumer;

    public KafkaMessagingWorker(KafkaMessagingProvider kafkaMessagingProvider, MessagingProviderOverrides overrides, String jobname) {
        this.provider = kafkaMessagingProvider;
        this.overrides = overrides;
        this.jobname = jobname;
    }

    @Override
    public boolean subscribe(String jobname, String selector) {
        if (interrupt) {
            return true;
        }
        this.topic = getTopic(provider);
        log.fine(String.format("subscribe job %s to topic [%s]", jobname, this.topic));
        if (this.topic != null) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (!isConnected()) {
                        if (!connect()) {
                            log.fine("Connect failed");
                            return false;
                        } else {
                            log.fine("Connect succeded");
                        }
                    } else {
                        log.fine("Already connected");
                    }
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

                    log.log(Level.SEVERE, "Exception raised while subscribing job '" + jobname + "', retrying in " + RETRY_MINUTES + " minutes.", ex);
                    if (!Thread.currentThread().isInterrupted()) {

                        unsubscribe(jobname);

                        try {
                            Thread.sleep(RETRY_MINUTES * 60 * 1000);
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
        if (interrupt) {
            log.info("We are being interrupted. Skipping unsubscribe...");
            return;
        }
        try {
            if (consumer != null) {
                log.info("Un-subscribing job '" + jobname + "' from " + this.topic + " topic.");
                consumer.close(); // TODO set timeout
            }
        } catch (Exception e) {
            log.warning(e.getMessage());
        }
        consumer = null;
    }

    @Override
    public void receive(String jobname, ProviderData pdata) {
        KafkaSubscriberProviderData pd = (KafkaSubscriberProviderData)pdata;
        int timeoutInMs = (pd.getTimeout() != null ? pd.getTimeout() : KafkaSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES) * 60 * 1000;
        if (interrupt) {
            log.info("we have been interrupted at start of receive");
            return;
        }
        while (!subscribe(jobname)) {
            if (!Thread.currentThread().isInterrupted()) {
                try {
                    int WAIT_SECONDS = 2;
                    Thread.sleep(WAIT_SECONDS * 1000);
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
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        long lastSeenMessage = new Date().getTime();
        try {
            while ((new Date().getTime() - lastSeenMessage) < timeoutInMs) {
                ConsumerRecords<String, String> records = consumer.poll(1000);
                val it = records.iterator();
                if(it.hasNext()) {
                    val rec = it.next();
                    log.fine(String.format("kafka message received [%s]", rec.toString()));
                    log.fine(String.format("kafka message received from [%s] [%s] [%s]", rec.topic(), rec.key(), rec.value()));
                    lastSeenMessage = new Date().getTime();
                    String value = rec.value();
                    if(pd.getChecks().size()>0) { // TODO refactor
                        KafkaMessage data = mapper.readValue(value, KafkaMessage.class);
                        if (provider.verify(data.getBody(), pd.getChecks(), jobname)) {
                            Map<String, String> params = new HashMap<String, String>();
                            params.put(pd.getVariable(), data.getBody());
                            params.put(pd.DEFAULT_HEADERS_NAME, rec.toString()); // TODO use parametrized name
                            trigger(jobname, provider.formatMessage(data), params);
                        }
                    } else {
                        Map<String, String> params = new HashMap<String, String>();
                        params.put(pd.getVariable(), value);
                        params.put(pd.DEFAULT_HEADERS_NAME, rec.toString()); // TODO use parametrized name
                        trigger(jobname, value, params);
                    }
                } else {
                    if (interrupt) {
                        log.info("We have been interrupted...");
                        //pollerClosed = true;
                        break;
                    } else {
                        log.fine(String.format("empty topic ", topic));
                    }
                }
            }
            if (!interrupt) {
                log.info("No message received for the past " + timeoutInMs + " ms, re-subscribing for job '" + jobname + "'.");
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

    }

    @Override
    public boolean connect() {
        // fix for org.apache.kafka.common.config.ConfigException:
        // Invalid value org.apache.kafka.common.serialization.StringDeserializer for configuration
        // value.deserializer: Class org.apache.kafka.common.serialization.StringDeserializer could not be found.
        Thread.currentThread().setContextClassLoader(null);
        consumer = new KafkaConsumer<>(provider.getConnectionPropertiesProperties());
        return true;
    }

    @Override
    public boolean isConnected() {
        return consumer != null;
    }

    @Override
    public boolean isConnectedAndSubscribed() {
        return isConnected();
    }

    @Override
    public void disconnect() {
    }

    @Override
    public SendResult sendMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        KafkaPublisherProviderData pd = (KafkaPublisherProviderData)pdata;
        String body = "";
        String msgId = "";
        Thread.currentThread().setContextClassLoader(null);
        try(KafkaProducer<String, String> producer = new KafkaProducer<>(provider.getConnectionPropertiesProperties())) {

            EnvVars env = new EnvVars();
            env.putAll(build.getEnvironment(listener));
            env.put("CI_NAME", build.getParent().getName());
            if (!build.isBuilding()) {
                env.put("CI_STATUS", (build.getResult() == Result.SUCCESS ? "passed" : "failed"));
                env.put("BUILD_STATUS", build.getResult().toString());
            }

            String ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener));
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
                    ltopic,
                    null,
                    Instant.now().toEpochMilli(),
                    UUID.randomUUID().toString(),
                    PluginUtils.getSubstitutedValue(pd.getMessageContent(), env)
            );
            body = producerRecord.value();
            msgId = producerRecord.key();

            producer.send(producerRecord);
            log.fine(String.format("message id: %s body: %s", producerRecord.key(), producerRecord.value()));
            listener.getLogger().println(String.format("message id: %s body: %s", producerRecord.key(), producerRecord.value()));

        } catch (Exception e) {
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
        }
        return new SendResult(true, msgId, body);
    }

    @Override
    public String waitForMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        KafkaSubscriberProviderData pd = (KafkaSubscriberProviderData)pdata;
        log.info("Waiting for message.");
        listener.getLogger().println("Waiting for message.");
        for (MsgCheck msgCheck: pd.getChecks()) {
            log.info(" with check: " + msgCheck.toString());
            listener.getLogger().println(" with check: " + msgCheck.toString());
        }
        Integer timeout = (pd.getTimeout() != null ? pd.getTimeout() : KafkaSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES);
        log.info(" with timeout: " + timeout);
        listener.getLogger().println(" with timeout: " + timeout);

        String ltopic = getTopic(provider);
        try {
            ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener));
        } catch (IOException | InterruptedException e) {
            log.warning(e.getMessage());
        }

        KafkaConsumer<String, String> lconsumer = new KafkaConsumer<>(provider.getConnectionPropertiesProperties());
        lconsumer.subscribe(Collections.singletonList(ltopic));

        ObjectMapper mapper = new ObjectMapper();
        long startTime = new Date().getTime();

        int timeoutInMs = timeout * 60 * 1000;
        boolean interrupted = false;
        try {
            while ((new Date().getTime() - startTime) < timeoutInMs) {

                ConsumerRecords<String, String> records = consumer.poll(1000);
                val it = records.iterator();
                if(it.hasNext()) {
                    val rec = it.next();
                    listener.getLogger().println(String.format("Received a message: %s", rec.toString()));
                    //log.fine(String.format("kafka message received [%s]", rec.toString()));
                    //log.fine(String.format("kafka message received from [%s] [%s] [%s]", rec.topic(), rec.key(), rec.value()));
                    String body = null;
                    if(pd.getChecks().size()>0) {
                        String json = rec.value();
                        KafkaMessage data = mapper.readValue(json, KafkaMessage.class);
                        body = data.getBody();
                        if (!provider.verify(body, pd.getChecks(), jobname)) {
                            continue;
                        }
                    } else {
                        body = rec.value();
                    }
                    if (build != null) {
                        if (StringUtils.isNotEmpty(pd.getVariable())) {
                            EnvVars vars = new EnvVars();
                            vars.put(pd.getVariable(), body);
                            build.addAction(new CIEnvironmentContributingAction(vars));
                        }
                    }
                    return body;
                }
            }
            if (interrupted) {
                return null;
            }
            log.severe("Timed out waiting for message!");
            listener.getLogger().println("Timed out waiting for message!");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
        } finally {
            try {
                lconsumer.close(); // TODO set timeout
            } catch (Exception e) {
                listener.getLogger().println("exception in finally");
            }
        }
        return null;
    }

    @Override
    public void prepareForInterrupt() {
    }

    @Override
    public boolean isBeingInterrupted() {
        return false;
    }

    @Override
    public String getDefaultTopic() {
        return DEFAULT_TOPIC;
    }

}
