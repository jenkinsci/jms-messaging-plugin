package com.redhat.jenkins.plugins.ci.messaging;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.MockedStatic;

import com.redhat.jenkins.plugins.ci.provider.data.KafkaSubscriberProviderData;
import com.redhat.utils.PluginUtils;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;

public class KafkaReadySubscriptionTest {
    @Rule
    public Timeout testTimeout = Timeout.seconds(20);
    private final KafkaMessagingProvider provider = mock(KafkaMessagingProvider.class);
    private final Run<?, ?> run = mock(Run.class);
    private final TopicPartition partition = new TopicPartition("reply", 0);
    private final RecordingConsumer consumer = new RecordingConsumer();
    private final KafkaSubscriberProviderData data = new KafkaSubscriberProviderData();
    private KafkaMessagingWorker worker;
    private MockedStatic<PluginUtils> substitutions;
    private boolean receiveDuringAssignment;

    @Before
    public void setup() throws Exception {
        substitutions = mockStatic(PluginUtils.class);
        substitutions.when(() -> PluginUtils.getSubstitutedValue(anyString(), any()))
                .thenAnswer(call -> call.getArgument(0));
        when(provider.getTopic()).thenReturn("reply");
        when(provider.getMergedConsumerProperties()).thenReturn(new Properties());
        when(provider.verify(anyString(), any(), anyString())).thenReturn(true);
        when(run.getEnvironment(any())).thenReturn(new EnvVars());
        data.setVariable("");
        data.setTimeout(1);
        worker = new KafkaMessagingWorker(provider, data, "job") {
            @Override
            protected Consumer<String, String> createWaitConsumer(Properties properties) {
                assertEquals("false", properties.getProperty("enable.auto.commit"));
                return KafkaReadySubscriptionTest.this.consumer;
            }
        };
        consumer.schedulePollTask(() -> {
            consumer.updateBeginningOffsets(Collections.singletonMap(partition, 0L));
            consumer.updateEndOffsets(Collections.singletonMap(partition, 4L));
            consumer.rebalance(Collections.singletonList(partition));
            if (receiveDuringAssignment) {
                consumer.addRecord(response(4, "{\"early\":true}"));
            }
        });
    }

    @After
    public void cleanup() {
        substitutions.close();
    }

    private ConsumerRecord<String, String> response(long offset, String json) {
        return new ConsumerRecord<>("reply", 0, offset, null, json);
    }

    @Test
    public void initializesLatestPositionBeforeBodyAndReceivesImmediateReply() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        String result = worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            assertTrue(consumer.assignment().contains(partition));
            assertEquals(4L, consumer.position(partition));
            consumer.schedulePollTask(() -> consumer.addRecord(response(4, "{\"result\":\"ok\"}")));
            return CompletableFuture.completedFuture(null);
        });
        assertEquals("{\"result\":\"ok\"}", result);
        assertEquals(5L, consumer.commits.get(partition).offset());
        assertTrue(consumer.closed());
        assertSame(original, Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void keepsPollingAndDoesNotCommitEarlyResponseWhileBodyRuns() {
        CompletableFuture<Void> body = new CompletableFuture<>();
        String result = worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            consumer.schedulePollTask(() -> consumer.addRecord(response(4, "{}")));
            consumer.schedulePollTask(() -> {
                assertTrue(consumer.commits.isEmpty());
                body.complete(null);
            });
            return body;
        });
        assertEquals("{}", result);
        assertTrue(consumer.polls >= 3);
        assertEquals(5L, consumer.commits.get(partition).offset());
    }

    @Test
    public void retainsRecordReturnedByTheAssignmentPoll() {
        receiveDuringAssignment = true;
        assertEquals("{\"early\":true}", worker.waitForMessage(run, TaskListener.NULL, data, null,
                () -> CompletableFuture.completedFuture(null)));
        assertEquals(5L, consumer.commits.get(partition).offset());
    }

    @Test
    public void bodyFailureClosesConsumerWithoutCommitting() {
        IOException failure = new IOException("request failed");
        assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null,
                () -> CompletableFuture.failedFuture(failure)));
        assertSame(failure, worker.getLastError().getCause());
        assertTrue(consumer.commits.isEmpty());
        assertTrue(consumer.closed());
    }

    @Test
    public void assignmentTimeoutNeverStartsBody() {
        data.setTimeout(0);
        AtomicBoolean called = new AtomicBoolean();
        assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            called.set(true);
            return CompletableFuture.completedFuture(null);
        }));
        assertFalse(called.get());
        assertNotNull(worker.getLastError());
        assertTrue(consumer.closed());
    }

    @Test
    public void wakeupCancelsPendingBodyWaitAndClosesConsumer() {
        assertNull(worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            consumer.schedulePollTask(worker::cancelWait);
            return new CompletableFuture<>();
        }));
        assertTrue(consumer.closed());
        assertTrue(consumer.commits.isEmpty());
    }

    @Test
    public void filteredRecordDoesNotEndWaitBeforeMatchingReply() {
        when(provider.verify(eq("{\"old\":true}"), any(), anyString())).thenReturn(false);
        String result = worker.waitForMessage(run, TaskListener.NULL, data, null, () -> {
            consumer.schedulePollTask(() -> {
                consumer.addRecord(response(4, "{\"old\":true}"));
                consumer.addRecord(response(5, "{\"new\":true}"));
            });
            return CompletableFuture.completedFuture(null);
        });
        assertEquals("{\"new\":true}", result);
        assertEquals(6L, consumer.commits.get(partition).offset());
    }

    private static class RecordingConsumer extends MockConsumer<String, String> {
        private final Map<TopicPartition, OffsetAndMetadata> commits = new HashMap<>();
        private int polls;

        RecordingConsumer() {
            super("latest");
        }

        @Override
        public synchronized ConsumerRecords<String, String> poll(Duration timeout) {
            polls++;
            return super.poll(timeout);
        }

        @Override
        public synchronized void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets) {
            commits.putAll(offsets);
            super.commitSync(offsets);
        }
    }
}
