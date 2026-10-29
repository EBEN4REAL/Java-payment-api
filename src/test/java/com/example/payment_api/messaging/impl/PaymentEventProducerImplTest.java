package com.example.payment_api.messaging.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.example.payment_api.config.PaymentEventsProperties;
import com.example.payment_api.enums.PaymentStatus;
import com.example.payment_api.event.PaymentCreatedEvent;

@ExtendWith(MockitoExtension.class)
class PaymentEventProducerImplTest {

    private static final String TOPIC = "payment-events";

    @Mock
    private KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;

    private PaymentEventProducerImpl producer;

    private final PaymentCreatedEvent event = new PaymentCreatedEvent(
            UUID.randomUUID(),
            Instant.now(),
            42L,
            "1234567890",
            "0987654321",
            new BigDecimal("5000.00"),
            "NGN",
            PaymentStatus.PENDING
    );

    @BeforeEach
    void setUp() {
        producer = new PaymentEventProducerImpl(
                kafkaTemplate, new PaymentEventsProperties(TOPIC, 3, (short) 1));
    }

    @Test
    void shouldSendEventKeyedByPaymentId() {
        // Arrange
        SendResult<String, PaymentCreatedEvent> result = new SendResult<>(
                new ProducerRecord<>(TOPIC, "42", event),
                new RecordMetadata(new TopicPartition(TOPIC, 1), 7L, 0, 0L, 2, 100));

        when(kafkaTemplate.send(TOPIC, "42", event))
                .thenReturn(CompletableFuture.completedFuture(result));

        // Act`
        producer.sendPaymentCreated(event);

        // Assert
        verify(kafkaTemplate).send(TOPIC, "42", event);
    }

    @Test
    void shouldNotThrowWhenBrokerRejectsTheSend() {
        // Arrange: the payment is already committed, so the request must not fail.
        when(kafkaTemplate.send(TOPIC, "42", event))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker unavailable")));

        // Act and Assert
        assertDoesNotThrow(() -> producer.sendPaymentCreated(event));
    }

    @Test
    void shouldNotThrowWhenSendFailsImmediately() {
        // Arrange: send() throws when broker metadata isn't available within max.block.ms.
        when(kafkaTemplate.send(TOPIC, "42", event))
                .thenThrow(new KafkaException("Send failed"));

        // Act and Assert
        assertDoesNotThrow(() -> producer.sendPaymentCreated(event));
    }
}
