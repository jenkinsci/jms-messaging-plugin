package com.redhat.jenkins.plugins.ci.messaging;

import static com.redhat.utils.MessageUtils.JSON_TYPE;

import java.io.IOException;
import java.io.StringReader;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.sql.Time;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.jenkins.plugins.ci.CIEnvironmentContributingAction;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.OrderedProperties;
import com.redhat.utils.PluginUtils;

import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

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
public class ActiveMqMessagingWorker extends JMSMessagingWorker {
    private static final Logger log = Logger.getLogger(ActiveMqMessagingWorker.class.getName());

    private final ActiveMqMessagingProvider provider;

    public static final String DEFAULT_TOPIC = "VirtualTopic.qe.ci.>";

    private Connection connection;
    private MessageConsumer subscriber;
    private String uuid = UUID.randomUUID().toString();

    public ActiveMqMessagingWorker(JMSMessagingProvider messagingProvider, MessagingProviderOverrides overrides, String jobname) {
        super(messagingProvider, overrides, jobname);
        this.provider = (ActiveMqMessagingProvider) messagingProvider;
    }

    @Override
    public boolean subscribe(String jobname, String selector) {
        this.topic = getTopic(provider);
        if (this.topic != null) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (connection == null && !connect()) {
                        return false;
                    }
                    if (subscriber == null) {
                        log.info("Subscribing job '" + jobname + "' to '" + this.topic + "' topic.");
                        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                        if (provider.getUseQueues()) {
                            Queue destination = session.createQueue(this.topic);
                            subscriber = session.createConsumer(destination, selector, false);
                        } else {
                            Topic destination = session.createTopic(this.topic);
                            subscriber = session.createDurableSubscriber(destination, jobname, selector, false);
                        }
                        log.info("Successfully subscribed job '" + jobname + "' to '" + this.topic + "' topic with selector: " + selector);
                    } else {
                        log.fine("Already subscribed to '" + this.topic + "' topic with selector: " + selector + " for job '" + jobname);
                    }
                    return true;
                } catch (JMSSecurityException | InvalidSelectorException exc) {
                    log.log(Level.SEVERE, "JMS exception raised while subscribing job '" + jobname + "'.", exc);
                    throw new RuntimeException(exc);
                } catch (JMSException ex) {

                    // Either we were interrupted, or something else went
                    // wrong. If we were interrupted, then we will jump ship
                    // on the next iteration. If something else happened,
                    // then we just unsubscribe here, sleep, so that we may
                    // try again on the next iteration.

                    log.log(Level.SEVERE, "JMS exception raised while subscribing job '" + jobname + "', retrying in " + RETRY_MINUTES + " minutes.", ex);
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
    public boolean connect() {
        connection = null;
        ConnectionFactory connectionFactory = provider.getConnectionFactory();

        Connection connectiontmp = null;
        try {
            connectiontmp = connectionFactory.createConnection();
            String url = "";
            if (Jenkins.getInstanceOrNull() != null) {
                url = Jenkins.get().getRootUrl();
            }
            connectiontmp.setClientID(provider.getName() + "_" + url + "_" + uuid + "_" + jobname);
            connectiontmp.start();
        } catch (JMSException e) {
            log.severe("Unable to connect to " + provider.getBroker() + " " + e.getMessage());
            disconnect(connectiontmp);
            return false;
        }
        log.info("Connection started");
        connection = connectiontmp;
        return true;
    }

    @Override
    public void unsubscribe(String jobname) {
        log.info("Unsubscribing job '" + jobname + "' from the '" + this.topic + "' topic.");
        disconnect();
        if (subscriber != null) {
            // Make sure the close doesn't clear the interrupt.
            boolean resetInterrupt = Thread.currentThread().isInterrupted();

            try {
                subscriber.close();
            } catch (Exception se) {
            }
            finally {
                subscriber = null;
            }

            if (resetInterrupt && !Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static String getMessageHeaders(Message message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();

            @SuppressWarnings("unchecked")
            Enumeration<String> e = message.getPropertyNames();
            while (e.hasMoreElements()) {
                String s = e.nextElement();
                if (message.getStringProperty(s) != null) {
                    root.set(s, mapper.convertValue(message.getObjectProperty(s), JsonNode.class));
                }
            }

            root.set("JMSCorrelationID", mapper.convertValue(message.getJMSCorrelationID(), JsonNode.class));
            root.set("JMSDeliveryMode", mapper.convertValue(new Integer(message.getJMSDeliveryMode()), JsonNode.class));
            root.set("JMSDestination", mapper.convertValue(message.getJMSDestination().toString(), JsonNode.class));
            root.set("JMSExpiration", mapper.convertValue(message.getJMSExpiration(), JsonNode.class));
            root.set("JMSMessageID", mapper.convertValue(message.getJMSMessageID(), JsonNode.class));
            root.set("JMSPriority", mapper.convertValue(message.getJMSPriority(), JsonNode.class));
            root.set("JMSRedelivered", mapper.convertValue(message.getJMSRedelivered(), JsonNode.class));
            root.set("JMSReplyTo", mapper.convertValue(message.getJMSReplyTo(), JsonNode.class));
            root.set("JMSTimestamp", mapper.convertValue(message.getJMSTimestamp(), JsonNode.class));
            root.set("JMSType", mapper.convertValue(message.getJMSType(), JsonNode.class));

            return mapper.writer().writeValueAsString(root);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception retrieving message headers:\n" + formatMessage(message), e);
        }

        return "";
    }

    public static String getMessageBody(Message message) {
        try {
            if (message instanceof MapMessage) {
                MapMessage mm = (MapMessage) message;
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.createObjectNode();

                @SuppressWarnings("unchecked")
                Enumeration<String> e = mm.getMapNames();
                while (e.hasMoreElements()) {
                    String field = e.nextElement();
                    root.set(field, mapper.convertValue(mm.getObject(field), JsonNode.class));
                }
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            } else if (message instanceof TextMessage) {
                TextMessage tm = (TextMessage) message;
                return tm.getText();
            } else if (message instanceof BytesMessage) {
                BytesMessage bm = (BytesMessage) message;
                bm.reset();
                byte[] bytes = new byte[(int) bm.getBodyLength()];
                bm.readBytes(bytes);
                return new String(bytes);
            } else {
                log.log(Level.SEVERE, "Unsupported message type:\n" + formatMessage(message));
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception retrieving message body:\n" + formatMessage(message), e);
        }

        return "";
    }

    private void process (String jobname, Message message) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("CI_MESSAGE", getMessageBody(message));
            params.put("MESSAGE_HEADERS", getMessageHeaders(message));

            @SuppressWarnings("unchecked")
            Enumeration<String> e = message.getPropertyNames();
            while (e.hasMoreElements()) {
                String s = e.nextElement();
                if (message.getStringProperty(s) != null) {
                    params.put(s, message.getObjectProperty(s).toString());
                }
            }
           super.trigger(jobname, formatMessage(message), params);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception processing message:\n" + formatMessage(message), e);
        }
    }

    @Override
    public void receive(String jobname, ProviderData pdata) {
        ActiveMQSubscriberProviderData pd = (ActiveMQSubscriberProviderData)pdata;
        int timeoutInMs = (pd.getTimeout() != null ? pd.getTimeout() : ActiveMQSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES) * 60 * 1000;
        while (!subscribe(jobname, pd.getSelector()) && !Thread.currentThread().isInterrupted()) {
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

        if (!Thread.currentThread().isInterrupted()) {
            try {
                Message m = subscriber.receive(timeoutInMs); // In milliseconds!
                if (m != null) {
                    if (provider.verify(getMessageBody(m), pd.getChecks(), jobname)) {
                        process(jobname, m);
                    }
                } else {
                    log.info("No message received for the past " + timeoutInMs + " ms, unsubscribing job '" + jobname + "'.");
                    unsubscribe(jobname);
                }
            } catch (JMSException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    // Something other than an interrupt causes this.
                    // Unsubscribe, but stay in our loop and try to reconnect..
                    log.log(Level.WARNING, "JMS exception raised while receiving, unsubscribing job '" + jobname + "'.", e);
                    unsubscribe(jobname); // Try again next time.
                }
            }
        } else {
            // We are about to leave the loop, so unsubscribe.
            unsubscribe(jobname);
        }
    }

    private void disconnect(Connection c) {
        if (c != null) {
            // Make sure the close doesn't clear the interrupt.
            boolean resetInterrupt = Thread.currentThread().isInterrupted();

            try {
                c.close();
            } catch (JMSException e) {
            } finally {
                c = null;
            }

            if (resetInterrupt && !Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void disconnect() {
        disconnect(connection);
        connection = null;
    }

    @Override
    public SendResult sendMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        ActiveMQPublisherProviderData pd = (ActiveMQPublisherProviderData)pdata;
        Connection connection = null;
        Session session = null;
        MessageProducer publisher = null;

        TextMessage message = null;
        String mesgId = "0";
        String mesgContent = "";

        try {
            String ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener));
            if (provider.getAuthenticationMethod() != null && ltopic != null && provider.getBroker() != null) {
                ConnectionFactory connectionFactory = provider.getConnectionFactory();
                connection = connectionFactory.createConnection();
                connection.start();

                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createTopic(ltopic);
                publisher = session.createProducer(destination);

                message = session.createTextMessage("");
                message.setJMSType(JSON_TYPE);

                TreeMap<String, String> envVarParts = new TreeMap<>();
                message.setStringProperty("CI_NAME", build.getParent().getName());
                envVarParts.put("CI_NAME", build.getParent().getName());

                if (pd.getMessageType() != null) {
                    message.setStringProperty("CI_TYPE", pd.getMessageType().getMessage());
                    envVarParts.put("CI_TYPE", pd.getMessageType().getMessage());
                }
                if (!build.isBuilding()) {
                    String ciStatus = (build.getResult()
                            == Result.SUCCESS ? "passed" : "failed");
                    message.setStringProperty("CI_STATUS", ciStatus);
                    envVarParts.put("CI_STATUS", ciStatus);

                    envVarParts.put("BUILD_STATUS", build.getResult().toString());
                }


                EnvVars baseEnvVars = build.getEnvironment(listener);
                EnvVars envVars = new EnvVars();
                envVars.putAll(baseEnvVars);
                envVars.putAll(envVarParts);

                if (!StringUtils.isEmpty(pd.getMessageProperties())) {
                    OrderedProperties p = new OrderedProperties();
                    p.load(new StringReader(PluginUtils.getSubstitutedValue(pd.getMessageProperties(), envVars)));
                    @SuppressWarnings("unchecked")
                    Enumeration<String> e = p.propertyNames();
                    while (e.hasMoreElements()) {
                        String key = e.nextElement();
                        if (!key.toLowerCase().startsWith("jms") || !setMessageHeader(message, key, p.getProperty(key), session)) {
                            EnvVars envVars2 = new EnvVars();
                            envVars2.putAll(baseEnvVars);
                            envVars2.putAll(envVarParts);
                            // This allows us to use recently added key/vals
                            // to be substituted
                            String val = PluginUtils.getSubstitutedValue(p.getProperty(key), envVars2);
                            message.setStringProperty(key, val);
                            envVarParts.put(key, val);
                        }
                    }
                }

                EnvVars envVars2 = new EnvVars();
                envVars2.putAll(baseEnvVars);
                envVars2.putAll(envVarParts);

                message.setText(PluginUtils.getSubstitutedValue(pd.getMessageContent(), envVars2));


                publisher.send(message);

                mesgId = message.getJMSMessageID();
                mesgContent = message.getText();

                String messageType = "unknown type";
                if (pd.getMessageType() != null) {
                    messageType = pd.getMessageType().toString();
                }
                log.info("Sent " + messageType + " message for job '" + build.getParent().getName() + "' to topic '" + ltopic + "':\n"
                        + formatMessage(message));
            } else {
                log.severe("One or more of the following is invalid (null): user, password, topic, broker.");
                return new SendResult(false, mesgId, mesgContent);
            }

        } catch (Exception e) {
            if (pd.isFailOnError()) {
                log.severe("Unhandled exception in perform: ");
                log.severe(ExceptionUtils.getStackTrace(e));
                listener.fatalError("Unhandled exception in perform: ");
                listener.fatalError(ExceptionUtils.getStackTrace(e));
                return new SendResult(false, mesgId, mesgContent);
            } else {
                log.warning("Unhandled exception in perform: ");
                log.warning(ExceptionUtils.getStackTrace(e));
                listener.error("Unhandled exception in perform: ");
                listener.error(ExceptionUtils.getStackTrace(e));
                return new SendResult(true, mesgId, mesgContent);
            }
        } finally {
            if (publisher != null) {
                try {
                    publisher.close();
                } catch (JMSException e) {
                }
            }
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                }
            }
        }
        return new SendResult(true, mesgId, mesgContent);
    }

    @Override
    public String waitForMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        ActiveMQSubscriberProviderData pd = (ActiveMQSubscriberProviderData)pdata;
        String ip = null;
        try {
            ip = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.severe("Unable to get localhost IP address.");
        }

        String ltopic = getTopic(provider);
        try {
            ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener));
        } catch (IOException e) {
            log.warning(e.getMessage());
        } catch (InterruptedException e) {
            log.warning(e.getMessage());
        }

        if (ip != null && provider.getAuthenticationMethod() != null && ltopic != null && provider.getBroker() != null) {
                log.info("Waiting for message with selector: " + pd.getSelector());
                listener.getLogger().println("Waiting for message with selector: " + pd.getSelector());
                Connection connection = null;
                MessageConsumer consumer = null;
                try {
                    ConnectionFactory connectionFactory = provider.getConnectionFactory();
                    connection = connectionFactory.createConnection();
                    connection.setClientID(ip + "_" + UUID.randomUUID().toString());
                    connection.start();
                    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    if (provider.getUseQueues()) {
                        Queue destination = session.createQueue(ltopic);
                        consumer = session.createConsumer(destination, pd.getSelector(), false);
                    } else {
                        Topic destination = session.createTopic(ltopic);
                        consumer = session.createDurableSubscriber(destination, jobname, pd.getSelector(), false);
                    }

                    long startTime = new Date().getTime();
                    Integer timeout = (pd.getTimeout() != null ? pd.getTimeout() : ActiveMQSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES)*60*1000;
                    Message message = null;
                    do {
                        message = consumer.receive(timeout);
                        if (message != null) {
                            String value = getMessageBody(message);
                            if (provider.verify(value, pd.getChecks(), jobname)) {
                                if (build != null) {
                                    if (StringUtils.isNotEmpty(pd.getVariable())) {
                                        EnvVars vars = new EnvVars();
                                        vars.put(pd.getVariable(), value);
                                        build.addAction(new CIEnvironmentContributingAction(vars));
                                    }
                                }
                                log.info("Received message with selector: " + pd.getSelector() + "\n" + formatMessage(message));
                                listener.getLogger().println("Received message with selector: " + pd.getSelector() + "\n" + formatMessage(message));
                                return value;
                            }
                        }
                    } while ((new Date().getTime() - startTime) < timeout && message != null);
                    log.info("Timed out waiting for message!");
                    listener.getLogger().println("Timed out waiting for message!");
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

    private static String formatHeaders (Message message) {
        Destination  dest = null;
        int delMode = 0;
        long expiration = 0;
        Time expTime = null;
        int priority = 0;
        String msgID = null;
        long timestamp = 0;
        Time timestampTime = null;
        String correlID = null;
        Destination replyTo = null;
        boolean redelivered = false;
        String type = null;

        StringBuilder sb = new StringBuilder();
        try {

            try {
                dest = message.getJMSDestination();
                sb.append("  JMSDestination: ");
                sb.append(dest);
                sb.append("\n");
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSDestination header\n", e);
            }

            try {
                delMode = message.getJMSDeliveryMode();
                if (delMode == DeliveryMode.NON_PERSISTENT) {
                    sb.append("  JMSDeliveryMode: non-persistent\n");
                } else if (delMode == DeliveryMode.PERSISTENT) {
                    sb.append("  JMSDeliveryMode: persistent\n");
                } else {
                    sb.append("  JMSDeliveryMode: neither persistent nor non-persistent; error\n");
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSDeliveryMode header\n", e);
            }

            try {
                expiration = message.getJMSExpiration();
                if (expiration != 0) {
                    expTime = new Time(expiration);
                    sb.append("  JMSExpiration: ");
                    sb.append(expTime);
                    sb.append("\n");
                } else {
                    sb.append("  JMSExpiration: 0\n");
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSExpiration header\n", e);
            }

            try {
                priority = message.getJMSPriority();
                sb.append("  JMSPriority: ");
                sb.append(priority);
                sb.append("\n");
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSPriority header\n", e);
            }

            try {
                msgID = message.getJMSMessageID();
                sb.append("  JMSMessageID: ");
                sb.append(msgID);
                sb.append("\n");
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSMessageID header\n", e);
            }

            try {
                timestamp = message.getJMSTimestamp();
                if (timestamp != 0) {
                    timestampTime = new Time(timestamp);
                    sb.append("  JMSTimestamp: ");
                    sb.append(timestampTime);
                    sb.append("\n");
                } else {
                    sb.append("  JMSTimestamp: 0\n");
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSTimestamp header\n", e);
            }

            try {
                correlID = message.getJMSCorrelationID();
                sb.append("  JMSCorrelationID: ");
                sb.append(correlID);
                sb.append("\n");
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSCorrelationID header\n", e);
            }

            try {
                replyTo = message.getJMSReplyTo();
                sb.append("  JMSReplyTo: ");
                sb.append(replyTo);
                sb.append("\n");
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSReplyTo header\n", e);
            }

            try {
                redelivered = message.getJMSRedelivered();
                sb.append("  JMSRedelivered: ");
                sb.append(redelivered);
                sb.append("\n");
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSRedelivered header\n", e);
            }

            try {
                type = message.getJMSType();
                sb.append("  JMSType: ");
                sb.append(type);
                sb.append("\n");
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to generate JMSType header\n", e);
            }

        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to generate JMS headers\n", e);
        }
        return sb.toString();
    }

    public static String formatMessage (Message message) {
        StringBuilder sb = new StringBuilder();

        try {
            String headers = formatHeaders(message);
            if (headers.length() > 0) {
                sb.append("Message Headers:\n");
                sb.append(headers);
            }

            sb.append("Message Properties:\n");
            @SuppressWarnings("unchecked")
            Enumeration<String> propNames = message.getPropertyNames();
            while (propNames.hasMoreElements()) {
                String propertyName = propNames.nextElement ();
                sb.append("  ");
                sb.append(propertyName);
                sb.append(": ");
                if (message.getObjectProperty(propertyName) != null) {
                    sb.append(message.getObjectProperty (propertyName).toString());
                }
                sb.append("\n");
            }

            sb.append("Message Body:\n");
            sb.append(getMessageBody(message));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unable to format message:", e);
        }

        return sb.toString();
    }

    private boolean setMessageHeader(Message m, String key, String value, Session session) {
        try {
            switch (key.toLowerCase()) {
            case "jmscorrelationid":
                m.setJMSCorrelationID(value);
                return true;
            case "jmsreplyto":
                Destination destination = session.createTopic(value);
                m.setJMSReplyTo(destination);
                return true;
            case "jmstype":
                m.setJMSType(value);
                return true;
            default:
                log.log(Level.WARNING, "Unable to set message header '" + key + "'.");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception setting message header '" + key + "'.", e);
        }
        return false;
    }

    @Override
    public String getDefaultTopic() {
        return DEFAULT_TOPIC;
    }
}
