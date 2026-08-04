package com.redhat.jenkins.plugins.ci.messaging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

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
}
