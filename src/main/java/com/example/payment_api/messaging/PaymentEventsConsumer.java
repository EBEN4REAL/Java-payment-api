package com.example.payment_api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.example.payment_api.event.PaymentCreatedEvent;

@Component
public class PaymentEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsConsumer.class);

    // Its own consumer group, so it tracks its own position in the topic.
    // A message that can't be deserialized is logged and skipped by Spring's default error handler.
    @KafkaListener(
            id = "payment-created-logger",
            groupId = "payment-created-logger",
            topics = "${payment.events.topic}",
            properties = "spring.json.value.default.type=com.example.payment_api.event.PaymentCreatedEvent"
    )
    public void onPaymentCreated(
            PaymentCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        // IDs only: account numbers stay out of the logs.
        log.info("Received PaymentCreated for payment {} (event {}) from partition {} offset {}",
                event.paymentId(), event.eventId(), partition, offset);
    }
}
