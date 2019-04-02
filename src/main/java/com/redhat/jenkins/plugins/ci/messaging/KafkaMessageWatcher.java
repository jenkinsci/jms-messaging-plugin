package com.redhat.jenkins.plugins.ci.messaging;

import static com.redhat.jenkins.plugins.ci.messaging.KafkaMessagingWorker.DEFAULT_TOPIC;

import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.messaging.data.KafkaMessage;
import lombok.val;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
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
public class KafkaMessageWatcher extends JMSMessageWatcher {

    private static final Logger log = Logger.getLogger(KafkaMessageWatcher.class.getName());

//    private ZMQ.Context lcontext;
//    private ZMQ.Poller lpoller;
//    private ZMQ.Socket lsocket;

    KafkaConsumer<String, String> consumer;

    private String topic;

    private KafkaMessagingProvider kafkaMessagingProvider;
    private boolean interrupted;

    public KafkaMessageWatcher(String jobname) {
        super(jobname);
    }

    @Override
    public String watch() {

        kafkaMessagingProvider = (KafkaMessagingProvider)provider;

        log.info("Waiting for message with selector: " + selector);
        for (MsgCheck msgCheck: checks) {
            log.info(" with check: " + msgCheck.toString());
        }
        taskListener.getLogger().println("Waiting for message with selector: " + selector);
        for (MsgCheck msgCheck: checks) {
            taskListener.getLogger().println(" with check: " + msgCheck.toString());
        }
        log.info(" with timeout: " + timeout);
        taskListener.getLogger().println(" with timeout: " + timeout);

        consumer = new KafkaConsumer<>(kafkaMessagingProvider.getConnectionPropertiesProperties());

        topic = PluginUtils.getSubstitutedValue(getTopic(overrides, kafkaMessagingProvider.getTopic(), DEFAULT_TOPIC), environment);

        consumer.subscribe(Collections.singletonList(topic));

        ObjectMapper mapper = new ObjectMapper();
        long startTime = new Date().getTime();

        int timeoutInMs = timeout * 60 * 1000;
        interrupted = false;
        try {
            while ((new Date().getTime() - startTime) < timeoutInMs) {
                ConsumerRecords<String, String> records = consumer.poll(1000);
                val it = records.iterator();
                if(it.hasNext()) {
                    val rec = it.next();
                    log.fine(String.format("kafka message received [%s]", rec.toString()));
                    log.fine(String.format("kafka message received from [%s] [%s] [%s]", rec.topic(), rec.key(), rec.value()));
                    String value = rec.value();
                    if(checks.size()>0) {
                        KafkaMessage data = mapper.readValue(value, KafkaMessage.class);
                        if (!provider.verify(data.getBody(), checks, jobname)) {
                            log.fine("message verification failed");
                            continue;
                        }
                        return data.getBody();
                    } else {
                        return value;
                    }
                }
            }
            if (interrupted) {
                return null;
            }
            log.severe("Timed out waiting for message!");
            taskListener.getLogger().println("Timed out waiting for message!");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
        } finally {
            try {
                consumer.close(); // TODO set timeout
                consumer = null;
            } catch (Exception e) {
                log.fine(e.getMessage());
            }
        }
        return null;
    }

    @Override
    public void interrupt() {}
}
