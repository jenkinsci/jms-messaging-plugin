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

import static com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingWorker.DEFAULT_TOPIC;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import com.redhat.utils.PluginUtils;

public class KafkaMessageWatcher extends JMSMessageWatcher {

    private static final Logger log = Logger.getLogger(KafkaMessageWatcher.class.getName());

    public KafkaMessageWatcher(String jobname) {
        super(jobname);
    }

    @Override
    public String watch() {

        KafkaMessagingProvider kprovider = (KafkaMessagingProvider) provider;

        log.info("Waiting for message");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kprovider.getMergedConsumerProperties())) {
            String topic = getTopic(overrides, kprovider.getTopic(), DEFAULT_TOPIC);
            topic = PluginUtils.getSubstitutedValue(topic, environment);
            consumer.subscribe(Collections.singletonList(topic));
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMinutes(timeout));
                Iterator<ConsumerRecord<String, String>> it = records.iterator();
                if (it.hasNext()) {
                    ConsumerRecord<String, String> rec = it.next();
                    log.fine(String.format("kafka message received [%s]", rec.toString()));
                    log.fine(String.format("kafka message received from [%s] [%s] [%s]", rec.topic(), rec.key(),
                            rec.value()));
                    if (provider.verify(rec.value(), checks, jobname)) {
                        return rec.value();
                    }
                } else {
                    log.severe("Timed out waiting for message!");
                    taskListener.getLogger().println("Timed out waiting for message!");
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
        }
        return null;
    }

    @Override
    public void interrupt() {
    }
}
