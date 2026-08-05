package com.redhat.jenkins.plugins.ci.messaging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Collections;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.jms.Message;

public class ActiveMqMessagingWorkerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Message newStubMessage() throws Exception {
        Message message = mock(Message.class);
        when(message.getPropertyNames()).thenReturn(Collections.emptyEnumeration());
        return message;
    }

    @Test
    public void getMessageHeaders_jmsReplyToWithActiveMQTopic() throws Exception {
        Message message = newStubMessage();
        ActiveMQTopic replyTopic = new ActiveMQTopic("VirtualTopic.some.reply.topic");
        when(message.getJMSReplyTo()).thenReturn(replyTopic);
        when(message.getJMSDestination()).thenReturn(new ActiveMQTopic("VirtualTopic.test"));

        String headers = ActiveMqMessagingWorker.getMessageHeaders(message);
        JsonNode root = mapper.readTree(headers);

        assertThat(root.get("JMSReplyTo").asText(), is("topic://VirtualTopic.some.reply.topic"));
    }

    @Test
    public void getMessageHeaders_nullJMSReplyTo() throws Exception {
        Message message = newStubMessage();
        when(message.getJMSReplyTo()).thenReturn(null);
        when(message.getJMSDestination()).thenReturn(new ActiveMQTopic("VirtualTopic.test"));

        String headers = ActiveMqMessagingWorker.getMessageHeaders(message);
        JsonNode root = mapper.readTree(headers);

        assertThat(root.get("JMSReplyTo").isNull(), is(true));
    }

    @Test
    public void getMessageHeaders_nullJMSDestination() throws Exception {
        Message message = newStubMessage();
        when(message.getJMSDestination()).thenReturn(null);

        String headers = ActiveMqMessagingWorker.getMessageHeaders(message);
        JsonNode root = mapper.readTree(headers);

        assertThat(root.get("JMSDestination").isNull(), is(true));
    }

    @Test
    public void getMessageHeaders_jmsDestinationWithActiveMQTopic() throws Exception {
        Message message = newStubMessage();
        when(message.getJMSDestination()).thenReturn(new ActiveMQTopic("VirtualTopic.test"));

        String headers = ActiveMqMessagingWorker.getMessageHeaders(message);
        JsonNode root = mapper.readTree(headers);

        assertThat(root.get("JMSDestination").asText(), is("topic://VirtualTopic.test"));
    }

    @Test
    public void connect_deafBroker_failsWithinTimeout() throws Exception {
        try (ServerSocket deafServer = new ServerSocket(0)) {
            int port = deafServer.getLocalPort();

            Thread acceptor = new Thread(() -> {
                try {
                    while (true) {
                        Socket s = deafServer.accept();
                        Thread.sleep(120_000);
                        s.close();
                    }
                } catch (Exception ignored) {
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(
                    "tcp://localhost:" + port + "?wireFormat.maxInactivityDuration=3000");
            factory.setConnectResponseTimeout(3_000);
            factory.setSendTimeout(3_000);
            ActiveMqMessagingProvider provider = mock(ActiveMqMessagingProvider.class);
            when(provider.getConnectionFactory()).thenReturn(factory);
            when(provider.getBroker()).thenReturn("tcp://localhost:" + port);
            when(provider.getName()).thenReturn("test-provider");

            ActiveMqMessagingWorker worker = new ActiveMqMessagingWorker(provider, null, "test-job");

            assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
                boolean connected = worker.connect();
                assertFalse("connect() should return false when broker is unresponsive", connected);
            }, "connect() hung for >20s against an unresponsive broker (issue #381)");
        }
    }

    @Test
    public void connect_staleBroker_failsWithinConnectResponseTimeout() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();

            Thread fakeServer = new Thread(() -> {
                try {
                    Socket s = server.accept();
                    DataInputStream din = new DataInputStream(s.getInputStream());
                    DataOutputStream dout = new DataOutputStream(s.getOutputStream());

                    // Read client's WireFormatInfo (size-prefixed frame)
                    int size = din.readInt();
                    byte[] frame = new byte[size];
                    din.readFully(frame);

                    // Echo it back — client accepts any valid WireFormatInfo
                    dout.writeInt(size);
                    dout.write(frame);
                    dout.flush();

                    // WireFormat negotiation complete — now go silent.
                    // Never respond to ConnectionInfo, simulating a stale broker.
                    Thread.sleep(120_000);
                    s.close();
                } catch (Exception ignored) {
                }
            });
            fakeServer.setDaemon(true);
            fakeServer.start();

            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:" + port);
            factory.setConnectResponseTimeout(3_000);
            ActiveMqMessagingProvider provider = mock(ActiveMqMessagingProvider.class);
            when(provider.getConnectionFactory()).thenReturn(factory);
            when(provider.getBroker()).thenReturn("tcp://localhost:" + port);
            when(provider.getName()).thenReturn("test-provider");

            ActiveMqMessagingWorker worker = new ActiveMqMessagingWorker(provider, null, "test-job");

            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                boolean connected = worker.connect();
                assertFalse("connect() should return false when broker goes silent after handshake", connected);
            }, "connect() hung against a stale broker — connectResponseTimeout did not fire (issue #381)");
        }
    }

    @Test
    public void connect_returnsFalseWhenConnectionFactoryIsNull() {
        ActiveMqMessagingProvider provider = mock(ActiveMqMessagingProvider.class);
        when(provider.getConnectionFactory()).thenReturn(null);
        when(provider.getBroker()).thenReturn("tcp://localhost:61616");
        when(provider.getName()).thenReturn("test-provider");

        ActiveMqMessagingWorker worker = new ActiveMqMessagingWorker(provider, null, "test-job");
        boolean connected = worker.connect();

        assertFalse("connect() should return false when factory is null", connected);
    }

    @Test
    public void getConnectionFactory_setsTimeouts() {
        ActiveMqMessagingProvider provider = mock(ActiveMqMessagingProvider.class,
                org.mockito.Mockito.CALLS_REAL_METHODS);
        ActiveMQConnectionFactory inputFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");

        org.apache.activemq.ActiveMQConnectionFactory result = provider.getConnectionFactory("tcp://localhost:61616",
                new com.redhat.jenkins.plugins.ci.authentication.activemq.ActiveMQAuthenticationMethod() {
                    @Override
                    public ActiveMQConnectionFactory getConnectionFactory(String broker) {
                        return inputFactory;
                    }

                    @Override
                    public hudson.model.Descriptor<com.redhat.jenkins.plugins.ci.authentication.activemq.ActiveMQAuthenticationMethod> getDescriptor() {
                        return null;
                    }
                });

        assertThat(result.getConnectResponseTimeout(), is(ActiveMqMessagingProvider.CONNECT_RESPONSE_TIMEOUT_MS));
        assertThat(result.getSendTimeout(), is(ActiveMqMessagingProvider.SEND_TIMEOUT_MS));
    }
}
