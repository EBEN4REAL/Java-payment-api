package com.example.payment_api.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.payment_api.event.PaymentCreatedEvent;
import com.example.payment_api.service.PaymentProcessingService;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentEventsConsumerTest {

    private static final String CREATED_JSON = """
            {"eventId":"6f1c2c1e-3a0b-4a53-9b1e-2f0c4f7d9a11","occurredAt":"2026-11-03T10:00:00Z",
             "paymentId":42,"sourceAccount":"1234567890","destinationAccount":"0987654321",
             "amount":5000.00,"currency":"NGN","status":"PENDING"}
            """;

    @Mock
    private PaymentProcessingService processingService;

    private PaymentEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentEventsConsumer(processingService, JsonMapper.builder().build());
    }

    @Test
    void shouldProcessPaymentCreated() {
        consumer.onPaymentEvent(CREATED_JSON, "PaymentCreated", "e-1", 0, 7L);

        ArgumentCaptor<PaymentCreatedEvent> event = ArgumentCaptor.forClass(PaymentCreatedEvent.class);
        verify(processingService).process(event.capture());
        assertEquals(42L, event.getValue().paymentId());
    }

    @Test
    void shouldIgnoreEventsThisServiceDoesNotHandle() {
        consumer.onPaymentEvent("{\"paymentId\":42}", "PaymentCompleted", "e-1", 0, 7L);

        verify(processingService, never()).process(any());
    }

    @Test
    void shouldRejectAMessageWithoutAnEventType() {
        assertThrows(NonRetryableEventException.class,
                () -> consumer.onPaymentEvent(CREATED_JSON, null, "e-1", 0, 7L));
    }

    @Test
    void shouldRejectAMalformedPayload() {
        assertThrows(NonRetryableEventException.class,
                () -> consumer.onPaymentEvent("not json", "PaymentCreated", "e-1", 0, 7L));
        verify(processingService, never()).process(any());
    }
}
