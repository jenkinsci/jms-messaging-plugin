package com.redhat.jenkins.plugins.ci.messaging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.jms.JMSException;
import jakarta.jms.Message;

public class ActiveMqMessagingWorkerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Message newStubMessage() throws Exception {
        Message message = mock(Message.class);
        when(message.getPropertyNames()).thenReturn(Collections.emptyEnumeration());
        return message;
    }

    private ActiveMqMessagingProvider newStubProvider() {
        ActiveMqMessagingProvider provider = mock(ActiveMqMessagingProvider.class);
        when(provider.getBroker()).thenReturn("failover:(ssl://broker1:61616,ssl://broker2:61616)");
        when(provider.getName()).thenReturn("test-provider");
        return provider;
    }

    @Test
    public void connect_returnsFalseWhenConnectionFactoryIsNull() throws Exception {
        // Simulates the boot-time race where credentials/SSL context are not
        // yet available: getConnectionFactory() returns null instead of
        // throwing. connect() must fail gracefully (so subscribe()'s retry
        // loop can try again) rather than letting a NullPointerException
        // escape and permanently kill the listener thread.
        ActiveMqMessagingProvider provider = newStubProvider();
        when(provider.getConnectionFactory()).thenReturn(null);

        ActiveMqMessagingWorker worker = new ActiveMqMessagingWorker(provider, null, "some-job");

        assertThat(worker.connect(), is(false));
    }

    @Test
    public void connect_appliesBoundedSendTimeoutToConnectionFactory() throws Exception {
        // Guards against a regression of the "hangs forever in
        // setClientID()" issue: every connection factory handed to
        // ActiveMQConnection must have a bounded send timeout so a stale
        // broker connection fails fast instead of blocking indefinitely.
        ActiveMqMessagingProvider provider = newStubProvider();
        ActiveMQConnectionFactory connectionFactory = mock(ActiveMQConnectionFactory.class);
        when(provider.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.createConnection()).thenThrow(new JMSException("simulated broker failure"));

        ActiveMqMessagingWorker worker = new ActiveMqMessagingWorker(provider, null, "some-job");

        assertThat(worker.connect(), is(false));
        verify(connectionFactory).setSendTimeout(60_000);
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
}
