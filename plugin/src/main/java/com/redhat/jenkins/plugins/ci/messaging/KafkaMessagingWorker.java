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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.redhat.jenkins.plugins.ci.CIEnvironmentContributingAction;
import com.redhat.jenkins.plugins.ci.messaging.checks.HeaderCheck;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.*;
import com.redhat.utils.OrderedProperties;
import com.redhat.utils.PluginUtils;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class KafkaMessagingWorker extends JMSMessagingWorker {
    private static final Logger log = Logger.getLogger(KafkaMessagingWorker.class.getName());
    private final KafkaMessagingProvider provider;
    private final KafkaProviderData pdata;
    static final String DEFAULT_TOPIC = "io.jenkins";
    KafkaConsumer<String, String> consumer;
    private volatile Consumer<String, String> waitingConsumer;

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
                    if (headerChecksMatch(rec.headers(), pd.getHeaderChecks())
                            && provider.verify(rec.value(), pd.getChecks(), jobname)) {
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
            Thread.currentThread().setContextClassLoader(KafkaConsumer.class.getClassLoader());
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
    public SendResult sendMessage(Run<?, ?> run, TaskListener listener, ProviderData pdata) {
        KafkaPublisherProviderData pd = (KafkaPublisherProviderData) pdata;
        String body = "";
        String msgId = "";
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(KafkaProducer.class.getClassLoader());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(
                pd.mergeProperties(provider.getMergedProducerProperties()))) {
            EnvVars env = new EnvVars();
            env.putAll(run.getEnvironment(listener));
            env.put("CI_NAME", run.getParent().getName());
            if (!run.isBuilding()) {
                String ciStatus = (run.getResult() == Result.SUCCESS ? "passed" : "failed");
                env.put("CI_STATUS", ciStatus);
                env.put("BUILD_STATUS", Objects.requireNonNull(run.getResult()).toString());
            }

            String ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), run.getEnvironment(listener));
            Iterable<org.apache.kafka.common.header.Header> headers = buildRecordHeaders(pd, env);
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(ltopic, null,
                    Instant.now().toEpochMilli(), UUID.randomUUID().toString(),
                    PluginUtils.getSubstitutedValue(pd.getMessageContent(), env), headers);
            body = producerRecord.value();
            msgId = producerRecord.key();

            producer.send(producerRecord).get();
            log.info(String.format("message id: %s body: %s", producerRecord.key(), producerRecord.value()));
            listener.getLogger()
                    .println(String.format("message id: %s body: %s", producerRecord.key(), producerRecord.value()));

        } catch (ExecutionException | IOException | InterruptedException e) {
            if (pd.getFailOnError()) {
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
    public String waitForMessage(Run<?, ?> run, TaskListener listener, ProviderData pdata, FilePath workspace) {
        KafkaSubscriberProviderData pd = (KafkaSubscriberProviderData) pdata;

        String ltopic = getTopic(provider);
        try {
            ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), run.getEnvironment(listener));
        } catch (IOException | InterruptedException e) {
            log.warning(e.getMessage());
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(KafkaConsumer.class.getClassLoader());
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
                if (headerChecksMatch(rec.headers(), pd.getHeaderChecks())
                        && provider.verify(rec.value(), pd.getChecks(), jobname)) {
                    return storeKafkaResponse(run, listener, pd, workspace, rec);
                }
            } else {
                log.warning("Timed out waiting for message!");
                listener.getLogger().println("Timed out waiting for message!");
            }
        } catch (IOException | InterruptedException e) {
            log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
        return null;
    }

    @Override
    public String getDefaultTopic() {
        return DEFAULT_TOPIC;
    }

    /** Consumer factory shared by the block wait and its protocol-level tests. */
    protected Consumer<String, String> createWaitConsumer(Properties properties) {
        return new KafkaConsumer<>(properties);
    }

    @Override
    public String waitForMessage(Run<?, ?> run, TaskListener listener, ProviderData pdata, FilePath workspace,
            SubscribedCallback onSubscribed) {
        if (onSubscribed == null) {
            return waitForMessage(run, listener, pdata, workspace);
        }
        KafkaSubscriberProviderData pd = (KafkaSubscriberProviderData) pdata;
        setLastError(null);
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Consumer<String, String> receiver = null;
        try {
            String responseTopic = PluginUtils.getSubstitutedValue(getTopic(provider), run.getEnvironment(listener));
            Properties properties = pd.mergeProperties(provider.getMergedConsumerProperties());
            // Polling must continue during the body, but must not commit an early
            // response before the body has completed successfully.
            properties.setProperty("enable.auto.commit", "false");
            Thread.currentThread().setContextClassLoader(KafkaConsumer.class.getClassLoader());
            receiver = createWaitConsumer(properties);
            waitingConsumer = receiver;
            receiver.subscribe(Collections.singletonList(responseTopic));
            int minutes = pd.getTimeout() != null ? pd.getTimeout()
                    : KafkaSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES;
            long waitMillis = TimeUnit.MINUTES.toMillis(minutes);
            long readinessDeadline = System.currentTimeMillis() + waitMillis;
            List<ConsumerRecord<String, String>> initial = new ArrayList<>();
            while (receiver.assignment().isEmpty() && System.currentTimeMillis() < readinessDeadline) {
                receiver.poll(
                        Duration.ofMillis(Math.min(100, Math.max(1, readinessDeadline - System.currentTimeMillis()))))
                        .forEach(initial::add);
            }
            if (receiver.assignment().isEmpty()) {
                setLastError(new TimeoutException("Timed out waiting for Kafka partition assignment."));
                return null;
            }
            // Assignment alone may precede offset initialization. Resolve every
            // position before publishing, especially with auto.offset.reset=latest.
            for (TopicPartition partition : receiver.assignment()) {
                long position = receiver.position(partition,
                        Duration.ofMillis(Math.max(1, readinessDeadline - System.currentTimeMillis())));
                log.fine("Kafka response position: " + partition + "=" + position);
            }
            listener.getLogger().println("Kafka subscription ready: topic=" + responseTopic);
            Future<Void> body = onSubscribed.run();
            ConsumerRecord<String, String> response = firstMatching(initial, pd);
            boolean bodyFinished = false;
            long responseDeadline = Long.MAX_VALUE;
            while (!Thread.currentThread().isInterrupted()) {
                if (body.isDone()) {
                    body.get();
                    if (!bodyFinished) {
                        bodyFinished = true;
                        responseDeadline = System.currentTimeMillis() + waitMillis;
                    }
                    if (response != null) {
                        listener.getLogger().println("Received a message: " + response);
                        String value = storeKafkaResponse(run, listener, pd, workspace, response);
                        receiver.commitSync(
                                Collections.singletonMap(new TopicPartition(response.topic(), response.partition()),
                                        new OffsetAndMetadata(response.offset() + 1)));
                        return value;
                    }
                    if (System.currentTimeMillis() >= responseDeadline) {
                        listener.getLogger().println("Timed out waiting for message!");
                        return null;
                    }
                }
                ConsumerRecords<String, String> records = receiver.poll(Duration.ofMillis(100));
                if (response == null) {
                    response = firstMatching(records, pd);
                }
                // Keep polling even after buffering a response: group membership
                // and rebalances must remain live while the Pipeline body runs.
            }
            throw new InterruptedException("Kafka subscription interrupted.");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            setLastError(failure);
        } catch (IOException | ExecutionException | RuntimeException failure) {
            setLastError(failure);
            log.log(Level.WARNING, "Failed waiting for Kafka response.", failure);
        } finally {
            waitingConsumer = null;
            boolean interrupted = Thread.interrupted();
            try {
                if (receiver != null) {
                    receiver.close(Duration.ofSeconds(5));
                }
            } catch (RuntimeException failure) {
                log.log(Level.WARNING, "Unable to close Kafka response consumer.", failure);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                Thread.currentThread().setContextClassLoader(original);
            }
        }
        return null;
    }

    private ConsumerRecord<String, String> firstMatching(Iterable<ConsumerRecord<String, String>> records,
            KafkaSubscriberProviderData pd) {
        for (ConsumerRecord<String, String> record : records) {
            if (record.value() != null && headerChecksMatch(record.headers(), pd.getHeaderChecks())
                    && provider.verify(record.value(), pd.getChecks(), jobname)) {
                return record;
            }
        }
        return null;
    }

    private String storeKafkaResponse(Run<?, ?> run, TaskListener listener, KafkaSubscriberProviderData pd,
            FilePath workspace, ConsumerRecord<String, String> record) throws IOException, InterruptedException {
        if (StringUtils.isNotEmpty(pd.getMessageVariable())) {
            String value = record.value();
            String json = consumerRecordToJson(record);
            if (pd.getUseFiles()) {
                if (value != null) {
                    workspace.child(pd.getMessageVariable()).write(value, String.valueOf(StandardCharsets.UTF_8));
                }
                workspace.child(pd.getRecordVariable()).write(json, String.valueOf(StandardCharsets.UTF_8));
            } else {
                EnvVars vars = new EnvVars();
                if (value != null) {
                    vars.put(pd.getMessageVariable(), value);
                }
                vars.put(pd.getRecordVariable(), json);
                run.addAction(new CIEnvironmentContributingAction(vars));
            }
        }
        return record.value();
    }

    @Override
    public void cancelWait() {
        Consumer<String, String> receiver = waitingConsumer;
        if (receiver != null) {
            try {
                receiver.wakeup();
            } catch (IllegalStateException closed) {
                log.fine("Kafka response consumer already closed.");
            }
        }
    }

    private static Iterable<org.apache.kafka.common.header.Header> buildRecordHeaders(KafkaPublisherProviderData pd,
            EnvVars env) {
        RecordHeaders recordHeaders = new RecordHeaders();
        putRecordHeader(recordHeaders, "CI_NAME", env.get("CI_NAME"));
        putRecordHeader(recordHeaders, "CI_STATUS", env.get("CI_STATUS"));
        putRecordHeader(recordHeaders, "BUILD_STATUS", env.get("BUILD_STATUS"));

        if (StringUtils.isEmpty(pd.getMessageHeaders())) {
            return recordHeaders;
        }
        try {
            OrderedProperties p = new OrderedProperties();
            p.load(new StringReader(PluginUtils.getSubstitutedValue(pd.getMessageHeaders(), env)));
            Enumeration<Object> e = p.propertyNames();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                String value = PluginUtils.getSubstitutedValue(p.getProperty(key), env);
                if (value != null) {
                    recordHeaders.remove(key);
                    recordHeaders.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException ex) {
            log.log(Level.WARNING, "Failed to parse message headers", ex);
        }
        return recordHeaders;
    }

    private static void putRecordHeader(RecordHeaders recordHeaders, String key, String value) {
        if (StringUtils.isEmpty(key) || value == null) {
            return;
        }
        recordHeaders.remove(key);
        recordHeaders.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean headerChecksMatch(org.apache.kafka.common.header.Headers headers,
            List<HeaderCheck> headerChecks) {
        if (headerChecks == null || headerChecks.isEmpty()) {
            return true;
        }
        for (HeaderCheck hc : headerChecks) {
            if (!hc.matches(headers)) {
                return false;
            }
        }
        return true;
    }

    private String consumerRecordToJson(ConsumerRecord<String, String> rec) {
        // Convert the consumer record to a JSON string, excluding value.
        ConsumerRecord<String, String> copy = new ConsumerRecord<>(rec.topic(), rec.partition(), rec.offset(),
                rec.timestamp(), rec.timestampType(), rec.serializedKeySize(), rec.serializedValueSize(), rec.key(),
                null, rec.headers(), rec.leaderEpoch());
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_NULL, Include.ALWAYS));
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
