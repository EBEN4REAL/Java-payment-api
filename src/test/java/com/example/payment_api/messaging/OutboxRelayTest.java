package com.example.payment_api.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.payment_api.config.OutboxProperties;
import com.example.payment_api.entity.OutboxEvent;
import com.example.payment_api.repository.OutboxEventRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(
                Duration.ofMillis(500), 100, Duration.ofSeconds(1), Duration.ofDays(7), "0 15 * * * *");
        relay = new OutboxRelay(
                outboxEventRepository, kafkaTemplate, properties, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC), new TracePropagation(Tracer.NOOP, Propagator.NOOP),
                meterRegistry, transactionManager);
    }

    @Test
    void shouldPublishEventsWithContractHeadersAndMarkThemPublished() {
        OutboxEvent event = event("e-1", "42", null);
        when(outboxEventRepository.lockNextUnpublished(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(succeeded());

        int published = relay.publishNextBatch();

        assertEquals(1, published);
        assertEquals(NOW, event.getPublishedAt());
        assertEquals(1, event.getAttempts());

        ProducerRecord<String, String> record = sentRecords(1).get(0);
        assertEquals("payment-events", record.topic());
        assertEquals("42", record.key());
        assertEquals("{\"paymentId\":42}", record.value());
        assertEquals("e-1", header(record, EventHeaders.EVENT_ID));
        assertEquals("PaymentCreated", header(record, EventHeaders.EVENT_TYPE));
        assertEquals(1.0, meterRegistry.counter("outbox.events.published").count());
    }

    @Test
    void shouldNotCopyTheSavedTraceContextOntoTheRecord() {
        // The saved context is restored as the current span instead, and KafkaTemplate's
        // observation writes traceparent. Copying it too would send two traceparent headers.
        OutboxEvent event = event("e-1", "42",
                "{\"traceparent\":\"00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01\"}");
        when(outboxEventRepository.lockNextUnpublished(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(succeeded());

        relay.publishNextBatch();

        assertNull(sentRecords(1).get(0).headers().lastHeader("traceparent"));
        assertEquals(NOW, event.getPublishedAt());
    }

    @Test
    void shouldKeepAnEventUnpublishedWhenKafkaRejectsIt() {
        OutboxEvent failing = event("e-1", "42", null);
        OutboxEvent ok = event("e-2", "43", null);
        when(outboxEventRepository.lockNextUnpublished(100)).thenReturn(List.of(failing, ok));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker rejected")))
                .thenReturn(succeeded());

        int published = relay.publishNextBatch();

        assertEquals(1, published);
        assertNull(failing.getPublishedAt());
        assertEquals(1, failing.getAttempts());
        assertNotNull(failing.getLastError());
        assertEquals(NOW, ok.getPublishedAt());
        assertEquals(1.0, meterRegistry.counter("outbox.events.publish.failures").count());
    }

    @Test
    void shouldStopTheBatchWhenKafkaIsUnreachable() {
        OutboxEvent first = event("e-1", "42", null);
        OutboxEvent second = event("e-2", "43", null);
        when(outboxEventRepository.lockNextUnpublished(100)).thenReturn(List.of(first, second));
        // send() throws when it can't reach the broker within max.block.ms.
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(new KafkaException("timeout"));

        int published = relay.publishNextBatch();

        assertEquals(0, published);
        assertEquals(1, first.getAttempts());
        // Not attempted: each try would have blocked for max.block.ms too.
        assertEquals(0, second.getAttempts());
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void shouldDoNothingWhenThereIsNothingToPublish() {
        when(outboxEventRepository.lockNextUnpublished(100)).thenReturn(List.of());

        assertEquals(0, relay.publishNextBatch());
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    private static OutboxEvent event(String eventId, String key, String headers) {
        return new OutboxEvent(eventId, "PaymentCreated", "payment-events", key, "{\"paymentId\":" + key + "}", headers);
    }

    private static CompletableFuture<SendResult<String, String>> succeeded() {
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    private List<ProducerRecord<String, String>> sentRecords(int expected) {
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(expected)).send(captor.capture());
        return captor.getAllValues();
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
