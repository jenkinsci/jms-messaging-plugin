package com.redhat.jenkins.plugins.ci.messaging;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.RegionBroker;
import org.apache.activemq.broker.region.TopicRegion;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.MockedStatic;

import com.redhat.jenkins.plugins.ci.authentication.activemq.ActiveMQAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.provider.data.ActiveMQSubscriberProviderData;
import com.redhat.utils.PluginUtils;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

public class ActiveMQReadySubscriptionTest {
    @Rule
    public Timeout testTimeout = Timeout.seconds(20);
    private final ActiveMqMessagingProvider provider = mock(ActiveMqMessagingProvider.class);
    private final Run<?, ?> run = mock(Run.class);
    private final ActiveMQSubscriberProviderData data = new ActiveMQSubscriberProviderData();
    private BrokerService broker;
    private ActiveMQConnectionFactory factory;
    private ActiveMqMessagingWorker worker;
    private MockedStatic<PluginUtils> substitutions;

    @Before
    public void setup() throws Exception {
        substitutions = mockStatic(PluginUtils.class);
        substitutions.when(() -> PluginUtils.getSubstitutedValue(anyString(), any()))
                .thenAnswer(call -> call.getArgument(0));
        broker = new BrokerService();
        broker.setBrokerName("ready-" + UUID.randomUUID());
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setAdvisorySupport(false);
        broker.start();
        broker.waitUntilStarted();
        factory = new ActiveMQConnectionFactory("vm://" + broker.getBrokerName() + "?create=false");
        when(provider.getConnectionFactory()).thenReturn(factory);
        when(provider.getAuthenticationMethod()).thenReturn(mock(ActiveMQAuthenticationMethod.class));
        when(provider.getBroker()).thenReturn(factory.getBrokerURL());
        when(provider.getTopic()).thenReturn("reply");
        when(provider.verify(anyString(), any(), anyString())).thenReturn(true);
        when(run.getEnvironment(any())).thenReturn(new EnvVars());
        data.setVariable("");
        data.setTimeout(1);
        worker = new ActiveMqMessagingWorker(provider, null, "job");
    }

    @After
    public void cleanup() throws Exception {
        substitutions.close();
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    private void publish() throws jakarta.jms.JMSException {
        try (Connection connection = factory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageProducer producer = session.createProducer(session.createTopic("reply"))) {
            producer.send(session.createTextMessage("{\"result\":\"ok\"}"));
        }
    }

    private void assertNoSubscriptionLeft() throws Exception {
        RegionBroker region = (RegionBroker) broker.getRegionBroker();
        assertTrue(((TopicRegion) region.getTopicRegion()).getDurableSubscriptions().isEmpty());
        assertEquals(0L, region.getDestinationMap().get(new ActiveMQTopic("reply")).getDestinationStatistics()
                .getConsumers().getCount());
    }

    @Test
    public void immediateTopicResponseIsRetainedAndNonDurableSubscriptionIsRemoved() throws Exception {
        String result = worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            try {
                publish();
            } catch (jakarta.jms.JMSException e) {
                throw new IOException(e);
            }
            return CompletableFuture.completedFuture(null);
        });
        assertEquals("{\"result\":\"ok\"}", result);
        assertNoSubscriptionLeft();
    }

    @Test
    public void failedBodyIsNotReplayedAndSubscriptionIsClosed() throws Exception {
        IOException failure = new IOException("body failed");
        int[] calls = { 0 };
        assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            calls[0]++;
            throw failure;
        }));
        assertSame(failure, worker.getLastError());
        assertEquals(1, calls[0]);
        assertNoSubscriptionLeft();
    }

    @Test
    public void asynchronousBodyFailureIsPreservedAndClosesSubscription() throws Exception {
        IOException failure = new IOException("asynchronous body failed");
        assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null,
                () -> CompletableFuture.failedFuture(failure)));
        assertSame(failure, worker.getLastError().getCause());
        assertNoSubscriptionLeft();
    }

    @Test
    public void interruptionClosesSubscription() throws Exception {
        try {
            assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
                throw new InterruptedException("cancelled");
            }));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertNoSubscriptionLeft();
    }

    @Test
    public void cancellationInterruptsBlockingReceiveAndClosesSubscription() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var task = executor.submit(() -> {
                try (MockedStatic<PluginUtils> local = mockStatic(PluginUtils.class)) {
                    local.when(() -> PluginUtils.getSubstitutedValue(anyString(), any()))
                            .thenAnswer(call -> call.getArgument(0));
                    return worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
                        ready.countDown();
                        return CompletableFuture.completedFuture(null);
                    });
                }
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue("Receiver must stop after cancellation", executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertNoSubscriptionLeft();
    }
}
