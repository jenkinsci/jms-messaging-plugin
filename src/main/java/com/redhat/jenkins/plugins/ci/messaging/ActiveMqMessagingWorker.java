package com.redhat.jenkins.plugins.ci.messaging;

import static com.redhat.utils.MessageUtils.JSON_TYPE;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.IOException;
import java.io.StringReader;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.sql.Time;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import jenkins.model.Jenkins;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.jenkins.plugins.ci.CIEnvironmentContributingAction;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.utils.MessageUtils;
import com.redhat.utils.OrderedProperties;
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
public class ActiveMqMessagingWorker extends JMSMessagingWorker {
    private static final Logger log = Logger.getLogger(ActiveMqMessagingWorker.class.getName());

    private final ActiveMqMessagingProvider provider;

    private Connection connection;
    private MessageConsumer subscriber;

    public ActiveMqMessagingWorker(ActiveMqMessagingProvider provider, MessagingProviderOverrides overrides, String jobname) {
        this.provider = provider;
        this.overrides = overrides;
        this.jobname = jobname;
    }

    @Override
    public boolean subscribe(String jobname, String selector) {
        this.topic = getTopic(provider);
        if (this.topic != null) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (!isConnected()) {
                        if (!connect()) {
                            return false;
                        }
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
        ActiveMQConnectionFactory connectionFactory = provider.getConnectionFactory();

        String ip = null;
        try {
            ip = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.severe("Unable to get localhost IP address.");
        }
        Connection connectiontmp = null;
        try {
            connectiontmp = connectionFactory
                    .createConnection();
            String url = "";
            if (Jenkins.getInstance() != null) {
                url = Jenkins.getInstance().getRootUrl();
            }
            connectiontmp.setClientID(provider.getName() + "_"
                    + url + "_" + ip + "_" + jobname);
            connectiontmp.start();
        } catch (JMSException e) {
            log.severe("Unable to connect to " + provider.getBroker() + " " + e.getMessage());
            // We need to close the connection that we attempted
            // to start.
            try {
                if (connectiontmp != null) {
                    connectiontmp.close();
                }
            } catch (JMSException e1) {
                //swallow the exception
            }
            return false;
        }
        log.info("Connection started");
        connection = connectiontmp;
        return true;
    }

    @Override
    public void unsubscribe(String jobname) {
        log.info("Unsubcribing job '" + jobname + "' from the '" + this.topic + "' topic.");
        disconnect();
        if (subscriber != null) {
            try {
                subscriber.close();
            } catch (Exception se) {
            }
            finally {
                subscriber = null;
            }
        }
    }

    public static String getMessageHeaders(Message message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();

            Enumeration<String> e = message.getPropertyNames();
            while (e.hasMoreElements()) {
                String s = e.nextElement();
                if (message.getStringProperty(s) != null) {
                    root.set(s, mapper.convertValue(message.getObjectProperty(s), JsonNode.class));
                }
            }

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
                return mapper.writer().writeValueAsString(root);
            } else if (message instanceof TextMessage) {
                TextMessage tm = (TextMessage) message;
                return tm.getText();
            } else if (message instanceof BytesMessage) {
                BytesMessage bm = (BytesMessage) message;
                byte[] bytes = new byte[(int) bm.getBodyLength()];
                if (bm.readBytes(bytes) == bm.getBodyLength()) {
                    return new String(bytes);
                }
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
            Map<String, String> params = new HashMap<String, String>();
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
    public void receive(String jobname, String selector, List<MsgCheck> checks, long timeoutInMs) {
        while (!subscribe(jobname, selector)) {
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
        try {
            Message m = subscriber.receive(timeoutInMs); // In milliseconds!
            if (m != null) {
                if (provider.verify(getMessageContent(m), checks)) {
                    process(jobname, m);
                }
            } else {
                log.info("No message received for the past " + timeoutInMs + " ms, re-subscribing job '" + jobname + "'.");
                unsubscribe(jobname);
            }
        } catch (JMSException e) {
            if (!Thread.currentThread().isInterrupted()) {
                // Something other than an interrupt causes this.
                // Unsubscribe, but stay in our loop and try to reconnect..
                log.log(Level.WARNING, "JMS exception raised while receiving, going to re-subscribe job '" + jobname + "'.", e);
                unsubscribe(jobname); // Try again next time.
            }
        }
    }

    @Override
    public boolean isConnected() {
        if (connection == null) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean isConnectedAndSubscribed() {
        if (connection != null && subscriber != null) {
            return true;
        } else {
            return false;
        }
    }
    @Override
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
            } finally {
                connection = null;
            }
        }
    }

    @Override
    public SendResult sendMessage(Run<?, ?> build, TaskListener listener,
                                  MessageUtils.MESSAGE_TYPE type, String props, String content, boolean failOnError) {
        Connection connection = null;
        Session session = null;
        MessageProducer publisher = null;

        TextMessage message = null;
        String mesgId = "0";
        String mesgContent = "";

        try {
            String ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener));
            if (provider.getAuthenticationMethod() != null && ltopic != null && provider.getBroker() != null) {
                ActiveMQConnectionFactory connectionFactory = provider.getConnectionFactory();
                connection = connectionFactory.createConnection();
                connection.start();

                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createTopic(ltopic);
                publisher = session.createProducer(destination);

                message = session.createTextMessage("");
                message.setJMSType(JSON_TYPE);

                TreeMap<String, String> envVarParts = new TreeMap<String, String>();
                message.setStringProperty("CI_NAME", build.getParent().getName());
                envVarParts.put("CI_NAME", build.getParent().getName());

                if (type != null) {
                    message.setStringProperty("CI_TYPE", type.getMessage());
                    envVarParts.put("CI_TYPE", type.getMessage());
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

                if (props != null && !props.trim().equals("")) {
                    OrderedProperties p = new OrderedProperties();
                    p.load(new StringReader(props));
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

                message.setText(PluginUtils.getSubstitutedValue(content, envVars2));

                mesgId = message.getJMSMessageID();
                mesgContent = message.getText();

                publisher.send(message);
                log.info("Sent " + type.toString() + " message for job '" + build.getParent().getName() + "' to topic '" + ltopic + "':\n"
                        + formatMessage(message));
            } else {
                log.severe("One or more of the following is invalid (null): user, password, topic, broker.");
                return new SendResult(false, mesgId, mesgContent);
            }

        } catch (Exception e) {
            if (failOnError) {
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
    public String waitForMessage(Run<?, ?> build, TaskListener listener,
                                 String selector, String variable,
                                 List<MsgCheck> checks,
                                 Integer timeout) {
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
                log.info("Waiting for message with selector: " + selector);
                listener.getLogger().println("Waiting for message with selector: " + selector);
                Connection connection = null;
                MessageConsumer consumer = null;
                try {
                    ActiveMQConnectionFactory connectionFactory = provider.getConnectionFactory();
                    connection = connectionFactory.createConnection();
                    connection.setClientID(ip + "_" + UUID.randomUUID().toString());
                    connection.start();
                    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    if (provider.getUseQueues()) {
                        Queue destination = session.createQueue(ltopic);
                        consumer = session.createConsumer(destination, selector, false);
                    } else {
                        Topic destination = session.createTopic(ltopic);
                        consumer = session.createDurableSubscriber(destination, jobname, selector, false);
                    }

                    Message message = consumer.receive(timeout*60*1000);
                    if (message != null) {
                        String value = getMessageBody(message);
                        if (build != null) {
                            if (StringUtils.isNotEmpty(variable)) {
                                EnvVars vars = new EnvVars();
                                vars.put(variable, value);
                                build.addAction(new CIEnvironmentContributingAction(vars));
                            }
                        }
                        log.info("Received message with selector: " + selector + "\n" + formatMessage(message));
                        listener.getLogger().println("Received message with selector: " + selector + "\n" + formatMessage(message));
                        return value;
                    }
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

    @Override
    public void prepareForInterrupt() {
    }

    @Override
    public boolean isBeingInterrupted() {
        return false;
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

            sb.append("Message Content:\n");
            sb.append(getMessageContent(message));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unable to format message:", e);
        }

        return sb.toString();
    }

    private static String getMessageContent(Message message) {
        StringBuilder sb = new StringBuilder();
        try {
            if (message instanceof TextMessage) {
                sb.append(((TextMessage) message).getText());
            } else if (message instanceof MapMessage) {
                MapMessage mm = (MapMessage) message;
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.createObjectNode();

                @SuppressWarnings("unchecked")
                Enumeration<String> e = mm.getMapNames();
                while (e.hasMoreElements()) {
                    String field = e.nextElement();
                    root.set(field, mapper.convertValue(mm.getObject(field), JsonNode.class));
                }
                sb.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            } else if (message instanceof BytesMessage) {
                BytesMessage bm = (BytesMessage) message;
                bm.reset();
                byte[] bytes = new byte[(int) bm.getBodyLength()];
                if (bm.readBytes(bytes) == bm.getBodyLength()) {
                    sb.append(new String(bytes));
                }
            } else {
                sb.append("  Unhandled message type: " + message.getJMSType());
            }
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
}
