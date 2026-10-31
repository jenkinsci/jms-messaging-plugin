package com.redhat.jenkins.plugins.ci.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.redhat.jenkins.plugins.ci.provider.data.RabbitMQSubscriberProviderData;
import com.redhat.utils.PluginUtils;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;

public class RabbitMQReadySubscriptionTest {
    private final RabbitMQMessagingProvider provider = mock(RabbitMQMessagingProvider.class);
    private final Connection connection = mock(Connection.class);
    private final Channel channel = mock(Channel.class);
    private final Channel cleanup = mock(Channel.class);
    private final Run<?, ?> run = mock(Run.class);
    private final AtomicReference<DeliverCallback> delivery = new AtomicReference<>();
    private final AtomicReference<String> queue = new AtomicReference<>();
    private RabbitMQSubscriberProviderData data;
    private RabbitMQMessagingWorker worker;
    private MockedStatic<PluginUtils> substitutions;

    @Before
    public void setup() throws Exception {
        substitutions = mockStatic(PluginUtils.class);
        substitutions.when(() -> PluginUtils.getSubstitutedValue(anyString(), any()))
                .thenAnswer(call -> call.getArgument(0));
        when(provider.getConnection()).thenReturn(connection);
        when(provider.getExchange()).thenReturn("requests");
        when(provider.getTopic()).thenReturn("request.response");
        when(provider.getQueue()).thenReturn("");
        when(provider.verify(anyString(), any(), anyString())).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel, cleanup);
        when(channel.isOpen()).thenReturn(true);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(new EnvVars());
        when(channel.basicConsume(anyString(), any(DeliverCallback.class), isNull(CancelCallback.class)))
                .thenAnswer(call -> {
                    queue.set(call.getArgument(0));
                    delivery.set(call.getArgument(1));
                    return "consumer";
                });
        data = new RabbitMQSubscriberProviderData();
        data.setVariable("");
        worker = new RabbitMQMessagingWorker(provider, null, "job");
    }

    @After
    public void teardown() {
        substitutions.close();
    }

    private void reply() throws IOException {
        delivery.get().handle("consumer", new Delivery(new Envelope(1L, false, "requests", "request.response"),
                new AMQP.BasicProperties(), "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void startsBodyOnlyAfterBindingAndConsumerAndKeepsImmediateResponse() throws Exception {
        String result = worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            InOrder order = inOrder(channel);
            order.verify(channel).queueDeclare(anyString(), eq(false), eq(true), eq(true), isNull());
            order.verify(channel).queueBind(queue.get(), "requests", "request.response");
            order.verify(channel).basicConsume(eq(queue.get()), any(DeliverCallback.class),
                    isNull(CancelCallback.class));
            reply(); // A response delivered before the body returns must not be lost.
            verify(channel, never()).basicAck(1L, false);
        });
        assertEquals("{\"result\":\"ok\"}", result);
        verify(channel).basicAck(1L, false);
        verify(cleanup).queueDelete(queue.get());
        verify(channel).close();
        verify(connection, never()).close();
    }

    @Test
    public void bodyFailureIsPreservedAndTemporaryQueueIsRemoved() throws Exception {
        IOException failure = new IOException("body failed");
        assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            throw failure;
        }));
        assertSame(failure, worker.getLastError());
        verify(cleanup).queueDelete(queue.get());
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    public void bindingFailureDoesNotStartBodyAndRemovesUnconsumedQueue() throws Exception {
        IOException failure = new IOException("binding denied");
        doThrow(failure).when(channel).queueBind(anyString(), anyString(), anyString());
        AtomicBoolean called = new AtomicBoolean();
        assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            called.set(true);
        }));
        assertFalse(called.get());
        assertSame(failure, worker.getLastError());
        verify(cleanup).queueDelete(anyString());
        verify(channel).close();
    }

    @Test
    public void cancellationInterruptsWaitAndCleansUp() throws Exception {
        try {
            assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
                throw new InterruptedException("cancelled");
            }));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(channel).close();
            verify(cleanup).queueDelete(queue.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void configuredQueueIsNeverDeleted() throws Exception {
        when(provider.getQueue()).thenReturn("existing");
        worker = new RabbitMQMessagingWorker(provider, null, "job");
        assertNotNull(worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            reply();
        }));
        verify(channel, never()).queueDeclare(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), any());
        verify(cleanup, never()).queueDelete(anyString());
    }

    @Test
    public void timeoutCleansUpWithoutAcknowledgingMessages() throws Exception {
        data.setTimeout(0);
        assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
        }));
        assertNull(worker.getLastError());
        verify(channel).close();
        verify(cleanup).queueDelete(queue.get());
    }

    @Test
    public void originalWaitOnlyOverloadStillReceivesMessages() throws Exception {
        when(channel.basicConsume(anyString(), any(DeliverCallback.class), isNull(CancelCallback.class)))
                .thenAnswer(call -> {
                    delivery.set(call.getArgument(1));
                    reply();
                    return "consumer";
                });
        assertNotNull(worker.waitForMessage(run, TaskListener.NULL, data, null));
        verify(channel).basicAck(1L, false);
        verify(channel).close();
    }
}
