package com.redhat.jenkins.plugins.ci.messaging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.apache.activemq.command.ActiveMQTopic;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.jms.Message;

public class ActiveMqMessagingWorkerTest {

    @Test
    public void getMessageHeadersHandlesJMSReplyToWithActiveMQTopic() throws Exception {
        Message message = mock(Message.class);
        ActiveMQTopic replyTopic = new ActiveMQTopic("VirtualTopic.some.reply.topic");
        when(message.getJMSReplyTo()).thenReturn(replyTopic);
        when(message.getJMSDestination()).thenReturn(new ActiveMQTopic("VirtualTopic.test"));
        when(message.getPropertyNames()).thenReturn(Collections.emptyEnumeration());

        String headers = ActiveMqMessagingWorker.getMessageHeaders(message);

        assertFalse("Headers should not be empty", headers.isEmpty());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(headers);
        assertThat(root.get("JMSReplyTo").asText(), containsString("VirtualTopic.some.reply.topic"));
    }

    @Test
    public void getMessageHeadersHandlesNullJMSReplyTo() throws Exception {
        Message message = mock(Message.class);
        when(message.getJMSReplyTo()).thenReturn(null);
        when(message.getJMSDestination()).thenReturn(new ActiveMQTopic("VirtualTopic.test"));
        when(message.getPropertyNames()).thenReturn(Collections.emptyEnumeration());

        String headers = ActiveMqMessagingWorker.getMessageHeaders(message);

        assertFalse("Headers should not be empty", headers.isEmpty());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(headers);
        assertThat("JMSReplyTo should be null node", root.get("JMSReplyTo").isNull(), org.hamcrest.Matchers.is(true));
    }
}
