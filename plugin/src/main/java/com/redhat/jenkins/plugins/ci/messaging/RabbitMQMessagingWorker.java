package com.redhat.jenkins.plugins.ci.messaging;

import com.rabbitmq.client.*;
import com.redhat.jenkins.plugins.ci.CIEnvironmentContributingAction;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.messaging.data.RabbitMQMessage;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQSubscriberProviderData;
import com.redhat.utils.PluginUtils;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

import jenkins.model.Jenkins;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RabbitMQMessagingWorker extends JMSMessagingWorker {

    private static final Logger log = Logger.getLogger(RabbitMQMessagingWorker.class.getName());
    private final RabbitMQMessagingProvider provider;

    private Connection connection;
    private Channel channel;
    // Thread interruption flag
    private boolean interrupt = false;
    private String uuid = UUID.randomUUID().toString();
    // Concurrent message queue used for saving messages from consumer
    private ConcurrentLinkedQueue<RabbitMQMessage> messageQueue = new ConcurrentLinkedQueue<RabbitMQMessage>();
    private String consumerTag = "";
    private String queueName = "";
    private String exchangeName = "";

    public RabbitMQMessagingWorker(JMSMessagingProvider messagingProvider, MessagingProviderOverrides overrides, String jobname) {
        super(messagingProvider, overrides, jobname);
        this.provider = (RabbitMQMessagingProvider) messagingProvider;
        this.connection = provider.getConnection();
        this.exchangeName = provider.getExchange();
        this.queueName = provider.getQueue();
        this.topic = getTopic(provider);
    }

    @Override
    public boolean subscribe(String jobname, String selector) {
        if (interrupt) {
            return true;
        }
        if (this.topic != null) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (connection == null || !connection.isOpen()) {
                        if (!connect()) {
                            return false;
                        }
                    }
                    if (channel == null || !channel.isOpen()) {
                        this.channel = connection.createChannel();
                        log.info("Subscribing job '" + jobname + "' to " + this.topic + " topic.");
                        channel.exchangeDeclarePassive(exchangeName);
                        String queueName = getQueue(provider);
                        channel.queueBind(queueName, exchangeName, this.topic);

                        // Create deliver callback to listen for messages
                        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                            String json = new String(delivery.getBody(), "UTF-8");
                            log.info(
                                    "Received '" + delivery.getEnvelope().getRoutingKey() + "':\n" + "Message id: '" + delivery.getProperties().getMessageId() + "'\n'" + json + "'");
                            RabbitMQMessage message = new RabbitMQMessage(delivery.getEnvelope().getRoutingKey(), json, delivery.getProperties().getMessageId());
                            message.setTimestamp(new Date().getTime());
                            message.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());
                            messageQueue.add(message);

                        };
                        this.consumerTag = channel.basicConsume(queueName, deliverCallback, (CancelCallback) null);
                        log.info("Successfully subscribed job '" + jobname + "' to topic '" + this.topic + "'.");
                    } else {
                        log.info("Already subscribed job '" + jobname + "' to topic '" + this.topic + "'.");
                    }
                    return true;
                } catch (Exception ex) {

                    // Either we were interrupted, or something else went
                    // wrong. If we were interrupted, then we will jump ship
                    // on the next iteration. If something else happened,
                    // then we just unsubscribe here, sleep, so that we may
                    // try again on the next iteration.

                    log.log(Level.SEVERE, "Eexception raised while subscribing job '" + jobname + "', retrying in " + RETRY_MINUTES + " minutes.", ex);
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
            channel.basicCancel(consumerTag);
            channel.close();
        } catch (Exception ex) {
            log.warning("Exception occurred when closing channel: " + ex.getMessage());
        }

    }

    @Override
    public void receive(String jobname, ProviderData pdata) {
        RabbitMQSubscriberProviderData pd = (RabbitMQSubscriberProviderData) pdata;
        Integer timeout = (pd.getTimeout() != null ? pd.getTimeout() : RabbitMQSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES) * 60 * 1000;

        if (interrupt) {
            log.info("we have been interrupted at start of receive");
            return;
        }

        // subscribe job
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

        long lastSeenMessage = new Date().getTime();

        try {
            while ((new Date().getTime() - lastSeenMessage) < timeout) {
                if (!messageQueue.isEmpty()) {
                    RabbitMQMessage data = messageQueue.poll();
                    // Reset timer
                    lastSeenMessage = data.getTimestamp().getTime();
                    //
                    if (provider.verify(data.getBodyJson(), pd.getChecks(), jobname)) {
                        Map<String, String> params = new HashMap<String, String>();
                        params.put("CI_MESSAGE", data.getBodyJson());
                        trigger(jobname, data.getBodyJson(), params);
                    }
                    channel.basicAck(data.getDeliveryTag(), false);
                } else {
                    if (interrupt) {
                        log.info("We have been interrupted...");
                        break;
                    }
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
        } catch (Exception e) {
            // If InterruptedException is caught, interrupt is cleared
            // and it needs to be set again
            // See https://stackoverflow.com/questions/7142665/why-does-thread-isinterrupted-always-return-false
            // for more info
            if (e.getClass() == InterruptedException.class) {
                Thread.currentThread().interrupt();
            }
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
        ConnectionFactory connectionFactory = provider.getConnectionFactory();
        Connection connectiontmp = null;
        try {
            connectiontmp = connectionFactory.newConnection();
            String url = "";
            if (Jenkins.getInstanceOrNull() != null) {
                url = Jenkins.get().getRootUrl();
            }
            connectiontmp.setId(provider.getName() + "_" + url + "_" + uuid + "_" + jobname);
        } catch (Exception e) {
            log.severe("Unable to connect to " + provider.getHostname() + ":" + provider.getPortNumber() + " " + e.getMessage());
            return false;
        }
        log.info("Connection created");
        connection = connectiontmp;
        provider.setConnection(connection);
        return true;
    }

    @Override
    public void disconnect() {
        try {
            channel.close();
        } catch (Exception ex) {
            log.warning("Exception occurred when closing channel: " + ex.getMessage());
        }
        try {
            connection.close();
        } catch (Exception ex) {
            log.warning("Exception occurred when closing connection: " + ex.getMessage());
        }
    }

    @Override
    public SendResult sendMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        RabbitMQPublisherProviderData pd = (RabbitMQPublisherProviderData)pdata;
        try {
            if (connection == null || !connection.isOpen()) {
                connect();
            }
            if (channel == null || !channel.isOpen()) {
                this.channel = connection.createChannel();
                log.info("Channel created.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        String body = "";
        String msgId = "";

        // Fedora messaging wire format support
        Map<String, Object> headers = new HashMap<String, Object>();
        if (pd.isFedoraMessaging()) {
            headers.put("fedora_messaging_severity", pd.getSeverity());
            headers.put("fedora_messaging_schema", pd.getSchema());
            headers.put("sent_at", ZonedDateTime.now().toString());
        }
        System.out.println(headers);
        try {

            EnvVars env = new EnvVars();
            env.putAll(build.getEnvironment(listener));
            env.put("CI_NAME", build.getParent().getName());
            if (!build.isBuilding()) {
                env.put("CI_STATUS", (build.getResult() == Result.SUCCESS ? "passed" : "failed"));
                env.put("BUILD_STATUS", build.getResult().toString());
            }

            RabbitMQMessage msg = new RabbitMQMessage(PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener)),
                                                     PluginUtils.getSubstitutedValue(pd.getMessageContent(), env));

            msg.setTimestamp(System.currentTimeMillis() / 1000L);

            body = msg.getBodyJson();
            msgId = msg.getMsgId();
            try {
                channel.exchangeDeclarePassive(exchangeName);
                channel.basicPublish(exchangeName, msg.getTopic(),
                        new AMQP.BasicProperties.Builder().headers(headers)
                        .messageId(msgId).build(), body.getBytes());
            } catch (IOException e) {
                if (pd.isFailOnError()) {
                    log.severe("Unhandled exception in perform: Failed to send message!");
                    return new SendResult(false, msgId, body);
                }
            }
            log.fine("Message headers:\n" + headers);
            log.fine("JSON message:\n" + msg.toJson());
            listener.getLogger().println("Message id: " + msg.getMsgId());
            listener.getLogger().println("Message topic: " + msg.getTopic());
            listener.getLogger().println("Message headers:\n" + headers);
            listener.getLogger().println("JSON message body:\n" + body);

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
        } finally {
            try {
                channel.close();
            } catch (Exception e) {
                log.warning("Unhandled exception when closing channel: ");
                log.warning(ExceptionUtils.getStackTrace(e));
                listener.getLogger().println("exception in finally");
            }
        }
        return new SendResult(true, msgId, body);
    }

    @Override
    public String waitForMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        RabbitMQSubscriberProviderData pd = (RabbitMQSubscriberProviderData)pdata;

        try {
            if (connection == null || !connection.isOpen()) {
                connect();
            }
            if (channel == null || !channel.isOpen()) {
                this.channel = connection.createChannel();
            }
            channel.exchangeDeclarePassive(exchangeName);
            channel.queueBind(getQueue(provider), exchangeName, this.topic);
        } catch (Exception ex) {
            log.severe("Connection to broker can't be established!");
            log.severe(ExceptionUtils.getStackTrace(ex));
            listener.error("Connection to broker can't be established!");
            listener.error(ExceptionUtils.getStackTrace(ex));
            return null;
        }

        log.info("Waiting for message.");
        listener.getLogger().println("Waiting for message.");
        for (MsgCheck msgCheck: pd.getChecks()) {
            log.info(" with check: " + msgCheck.toString());
            listener.getLogger().println(" with check: " + msgCheck.toString());
        }
        Integer timeout = (pd.getTimeout() != null ? pd.getTimeout() : RabbitMQSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES);
        log.info(" with timeout: " + timeout + " minutes");
        listener.getLogger().println(" with timeout: " + timeout + " minutes");


        // Create deliver callback to listen for messages
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String json = new String(delivery.getBody(), "UTF-8");
            listener.getLogger().println(
                    "Received '" + delivery.getEnvelope().getRoutingKey() + "':\n" + "Message id: '" + delivery.getProperties().getMessageId() + "'\n'" + json + "'");
            log.info(
                    "Received '" + delivery.getEnvelope().getRoutingKey() + "':\n" + "Message id: '" + delivery.getProperties().getMessageId() + "'\n'" + json + "'");
            RabbitMQMessage message = new RabbitMQMessage(delivery.getEnvelope().getRoutingKey(), json, delivery.getProperties().getMessageId());
            message.setTimestamp(new Date().getTime());
            message.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());
            messageQueue.add(message);

        };

        String consumerTag = null;

        long startTime = new Date().getTime();

        int timeoutInMs = timeout * 60 * 1000;

        try {
            consumerTag = channel.basicConsume(getQueue(provider), deliverCallback, (CancelCallback) null);
            while ((new Date().getTime() - startTime) < timeoutInMs) {
                if (!messageQueue.isEmpty()) {

                    RabbitMQMessage message = messageQueue.poll();
                    log.info("Obtained message from queue: " + message.toJson());

                    if (!provider.verify(message.getBodyJson(), pd.getChecks(), jobname)) {
                        channel.basicAck(message.getDeliveryTag(), false);
                        continue;
                    }
                    listener.getLogger().println(
                            "Message: '" + message.getMsgId() + "' was succesfully checked.");

                    if (build != null) {
                        if (StringUtils.isNotEmpty(pd.getVariable())) {
                            EnvVars vars = new EnvVars();
                            vars.put(pd.getVariable(), message.getBodyJson());
                            build.addAction(new CIEnvironmentContributingAction(vars));
                        }
                    }
                    channel.basicAck(message.getDeliveryTag(), false);
                    return message.getBodyJson();
                }
                if (interrupt) {
                    return null;
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
            log.severe("Timed out waiting for message!");
            listener.getLogger().println("Timed out waiting for message!");
        } catch (Exception e) {
            // If InterruptedException is caught, interrupt is cleared
            // and it needs to be set again
            // See https://stackoverflow.com/questions/7142665/why-does-thread-isinterrupted-always-return-false
            // for more info
            if (e.getClass() == InterruptedException.class) {
                Thread.currentThread().interrupt();
            }

            log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
        } finally {
            try {
                if (consumerTag != null) {
                    channel.basicCancel(consumerTag);
                }
                channel.close();
            } catch (Exception e) {
                listener.getLogger().println("exception in finally");
            }
        }
        return null;
    }

    public void prepareForInterrupt() {
        interrupt = true;
    }

    public boolean isBeingInterrupted() {
        return interrupt;
    }

    @Override
    public String getDefaultTopic() {
        return null;
    }

    protected String getQueue(JMSMessagingProvider provider) throws IOException {
        String ltopic;
        RabbitMQMessagingProvider providerd = (RabbitMQMessagingProvider) provider;
        if (overrides != null && overrides.getQueue() != null && !overrides.getQueue().isEmpty()) {
            ltopic = overrides.getQueue();
        } else if (providerd.getQueue() != null && !providerd.getQueue().isEmpty()) {
            ltopic = providerd.getQueue();
        } else {
            // The queue is not set anywhere, let's use random queue
            if (queueName.isEmpty()) {
                queueName = channel.queueDeclare().getQueue();
            }
            ltopic = queueName;
        }
        return PluginUtils.getSubstitutedValue(ltopic, null);
    }
}
