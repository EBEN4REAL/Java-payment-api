package com.example.payment_api.messaging.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.payment_api.config.PaymentEventsProperties;
import com.example.payment_api.entity.OutboxEvent;
import com.example.payment_api.enums.PaymentStatus;
import com.example.payment_api.event.PaymentCreatedEvent;
import com.example.payment_api.messaging.TracePropagation;
import com.example.payment_api.repository.OutboxEventRepository;
import com.jayway.jsonpath.JsonPath;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldStoreTheEventAsJsonKeyedByPaymentId() {
        OutboxServiceImpl outboxService = new OutboxServiceImpl(
                outboxEventRepository,
                new PaymentEventsProperties("payment-events", "payment-events-dlt", 3, (short) 1),
                JsonMapper.builder().build(),
                new TracePropagation(Tracer.NOOP, Propagator.NOOP));

        PaymentCreatedEvent event = new PaymentCreatedEvent(
                UUID.randomUUID(), Instant.parse("2026-11-03T10:15:30Z"), 42L,
                "1234567890", "0987654321", new BigDecimal("5000.00"), "NGN", PaymentStatus.PENDING);

        outboxService.enqueue(event);

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(saved.capture());
        OutboxEvent row = saved.getValue();

        assertEquals(event.eventId().toString(), row.getEventId());
        assertEquals("PaymentCreated", row.getEventType());
        assertEquals("payment-events", row.getTopic());
        assertEquals("42", row.getMessageKey());
        assertNull(row.getPublishedAt());
        assertNull(row.getHeaders()); // No active trace.

        // The payload is the event's fields only: the type travels in a header.
        String json = row.getPayload();
        assertEquals(42, (Integer) JsonPath.read(json, "$.paymentId"));
        assertEquals("2026-11-03T10:15:30Z", JsonPath.read(json, "$.occurredAt"));
        assertEquals(5000.00, (Double) JsonPath.read(json, "$.amount"));
        assertFalse(json.contains("eventType"));
    }
}
