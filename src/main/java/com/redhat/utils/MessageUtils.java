package com.redhat.utils;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.sql.Time;
import java.util.Enumeration;
import java.util.Properties;
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
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang.text.StrSubstitutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;

public class MessageUtils {

    private static final Logger log = Logger.getLogger(MessageUtils.class.getName());

    private static final String JSON_TYPE = "application/json";

    public static enum MESSAGE_TYPE {
        CodeQualityChecksDone("code-quality-checks-done"),
        ComponentBuildDone("component-build-done"),
        Custom("custom"),
        EarlyPerformanceTestingDone("early-performance-testing-done"),
        EarlySecurityTestingDone("early-security-testing-done"),
        ImageUploaded("image-uploaded"),
        FunctionalTestCoverageDone("functional-test-coverage-done"),
        FunctionalTestingDone("functional-testing-done"),
        NonfunctionalTestingDone("nonfunctional-testing-done"),
        OotbTestingDone("ootb-testing-done"),
        PeerReviewDone("peer-review-done"),
        ProductAcceptedForReleaseTesting("product-accepted-for-release-testing"),
        ProductBuildDone("product-build-done"),
        ProductBuildInStaging("product-build-in-staging"),
        ProductTestCoverageDone("product-test-coverage-done"),
        PullRequest("pull-request"),
        SecurityChecksDone("security-checks-done"),
        TestingStarted("testing-started"),
        TestingCompleted("testing-completed"),
        Tier0TestingDone("tier-0-testing-done"),
        Tier1TestingDone("tier-1-testing-done"),
        Tier2IntegrationTestingDone("tier-2-integration-testing-done"),
        Tier2ValidationTestingDone("tier-2-validation-testing-done"),
        Tier3TestingDone("tier-3-testing-done"),
        UnitTestCoverageDone("unit-test-coverage-done"),
        UpdateDefectStatus("update-defect-status");

        private String message;

        MESSAGE_TYPE(String value) {
            this.message = value;
        }

        public String getMessage() {
            return message;
        }

        public static MESSAGE_TYPE fromString(String value) {
            for (MESSAGE_TYPE t : MESSAGE_TYPE.values()) {
                if (value.equalsIgnoreCase(t.name())) {
                    return t;
                }
            }
            return null;
        }

        public String toDisplayName() {
            String v = name();
            if (v == null || v.isEmpty())
                return v;

            StringBuilder result = new StringBuilder();
            result.append(v.charAt(0));
            for (int i = 1; i < v.length(); i++) {
                if (Character.isUpperCase(v.charAt(i))) {
                    result.append(" ");
                }
                result.append(v.charAt(i));
            }
            return result.toString();
        }
    }

    public static boolean sendMessage(Run<?, ?> build, TaskListener listener, MESSAGE_TYPE type, String props, String content) throws InterruptedException, IOException {
        log.info("Sending CI message for job '" + build.getParent().getName() + "'.");
        GlobalCIConfiguration config = GlobalCIConfiguration.get();

        Connection connection = null;
        Session session = null;
        MessageProducer publisher = null;

        try {
            String user = config.getUser();
            String password = null;
            if (config.getPassword() != null) {
                password = config.getPassword().getPlainText();
            }
            String broker = config.getBroker();
            String topic = config.getTopic();

            if (user != null && config.getPassword() != null && topic != null && broker != null) {
                ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(user, password, broker);
                connection = connectionFactory.createConnection();
                connection.start();

                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createTopic(topic);
                publisher = session.createProducer(destination);

                TextMessage message;
                message = session.createTextMessage("");
                message.setJMSType(JSON_TYPE);

                message.setStringProperty("CI_NAME", build.getParent().getName());
                message.setStringProperty("CI_TYPE", type.getMessage());
                if (!build.isBuilding()) {
                    message.setStringProperty("CI_STATUS", (build.getResult()== Result.SUCCESS ? "passed" : "failed"));
                }

                StrSubstitutor sub = new StrSubstitutor(build.getEnvironment(listener));

                if (props != null && !props.trim().equals("")) {
                    Properties p = new Properties();
                    p.load(new StringReader(props));
                    @SuppressWarnings("unchecked")
                    Enumeration<String> e = (Enumeration<String>) p.propertyNames();
                    while (e.hasMoreElements()) {
                        String key = e.nextElement();
                        message.setStringProperty(key, sub.replace(p.getProperty(key)));
                    }
                }

                message.setText(sub.replace(content));

                publisher.send(message);
                log.info("Sent " + type.toString() + " message for job '" + build.getParent().getName() + "':\n" + MessageUtils.formatMessage(message));
            } else {
                log.severe("One or more of the following is invalid (null): user, password, topic, broker.");
                return false;
            }

        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception in perform.", e);
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
        return true;
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
                    root.put(field, mapper.convertValue(mm.getObject(field), JsonNode.class));
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
                log.log(Level.SEVERE, "Unsupported message type:\n" + MessageUtils.formatMessage(message));
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception retrieving message body:\n" + MessageUtils.formatMessage(message), e);
        }

        return "";
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
                    root.put(field, mapper.convertValue(mm.getObject(field), JsonNode.class));
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


    public static Message waitForMessage(Integer timeout, String selector) {
        return waitForMessage(timeout, null, selector);
    }

    private static void logIfPossible(PrintStream stream, String logMessage) {
        if (stream != null) stream.println(logMessage);
    }

    public static Message waitForMessage(Integer timeout, PrintStream stream, String selector) {

        String ip = null;
        try {
            ip = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.severe("Unable to get localhost IP address.");
            logIfPossible(stream, "Unable to get localhost IP address.");
        }

        GlobalCIConfiguration config = GlobalCIConfiguration.get();
        if (config != null) {
            String user = config.getUser();
            String broker = config.getBroker();
            String topic = config.getTopic();

            if (ip != null && user != null && config.getPassword() != null && topic != null && broker != null) {
                log.info("Waiting for message with selector: " + selector);
                logIfPossible(stream, "Waiting for message with selector: " + selector);
                Connection connection = null;
                MessageConsumer consumer = null;
                String password = config.getPassword().getPlainText();
                try {
                    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(user, password, broker);
                    connection = connectionFactory.createConnection();
                    connection.setClientID(ip + "_" + UUID.randomUUID().toString());
                    connection.start();
                    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    Topic destination = session.createTopic(topic);

                    consumer = session.createConsumer(destination, selector);

                    Message message = consumer.receive(timeout*60*1000);
                    if (message == null) {
                        log.warning("Timed out waiting for message!");
                        logIfPossible(stream, "Warning: Timed out waiting for message!");
                    } else {
                        log.info("Message received with selector: " + selector);
                        logIfPossible(stream, "Message received with selector: " + selector);
                    }
                    return message;
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
                    logIfPossible(stream, "Error: Unhandled exception " +
                            "waiting for message." + e.getMessage());
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
                logIfPossible(stream, "Error: One or more of the following is invalid (null): ip, user, password, topic, broker.");
            }
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
}
