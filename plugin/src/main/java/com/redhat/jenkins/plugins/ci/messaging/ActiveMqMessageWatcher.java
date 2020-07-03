package com.redhat.jenkins.plugins.ci.messaging;

import static com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingWorker.DEFAULT_TOPIC;
import static com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingWorker.formatMessage;
import static com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingWorker.getMessageBody;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import com.redhat.utils.PluginUtils;

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
public class ActiveMqMessageWatcher extends JMSMessageWatcher {

    private static final Logger log = Logger.getLogger(ActiveMqMessageWatcher.class.getName());
    private ActiveMqMessagingProvider activeMqMessagingProvider;
    private String topic;

    public ActiveMqMessageWatcher(String jobname) {
        super(jobname);
    }

    @Override
    public String watch() {

        activeMqMessagingProvider = (ActiveMqMessagingProvider)provider;

        String ip = null;
        try {
            ip = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.severe("Unable to get localhost IP address.");
        }

        topic = PluginUtils.getSubstitutedValue(getTopic(overrides, activeMqMessagingProvider.getTopic(), DEFAULT_TOPIC), environment);

        if (ip != null && activeMqMessagingProvider.getAuthenticationMethod() != null && topic != null && activeMqMessagingProvider.getBroker() != null) {
            log.info("Waiting for message with selector: " + selector);
            taskListener.getLogger().println("Waiting for message with selector: " + selector);
            Connection connection = null;
            MessageConsumer consumer = null;
            try {
                ConnectionFactory connectionFactory = activeMqMessagingProvider.getConnectionFactory();
                connection = connectionFactory.createConnection();
                connection.setClientID(ip + "_" + UUID.randomUUID().toString());
                connection.start();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                if (activeMqMessagingProvider.getUseQueues()) {
                    Queue destination = session.createQueue(topic);
                    consumer = session.createConsumer(destination, selector, false);
                } else {
                    Topic destination = session.createTopic(topic);
                    consumer = session.createDurableSubscriber(destination, UUID.randomUUID().toString(), selector, false);
                }

                Message message = consumer.receive(timeout*60*1000);
                if (message != null) {
                    String value = getMessageBody(message);
                    log.info("Received message with selector: " + selector + "\n" + formatMessage(message));
                    taskListener.getLogger().println("Received message with selector: " + selector + "\n" + formatMessage(message));
                    return value;
                }
                log.info("Timed out waiting for message!");
                taskListener.getLogger().println("Timed out waiting for message!");
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
            } finally {
                if (consumer != null) {
                    try {
                        consumer.close();
                    } catch (Exception e) {
                    }
                }
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception e) {
                    }
                }
            }
        } else {
            log.severe("One or more of the following is invalid (null): ip, user, password, topic, broker.");
        }
        return null;
    }

    @Override
    public void interrupt() {
    }
}
