package com.example.payment_api.messaging.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.payment_api.config.PaymentEventsProperties;
import com.example.payment_api.event.PaymentCreatedEvent;
import com.example.payment_api.messaging.PaymentEventProducer;

@Component
public class PaymentEventProducerImpl implements PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducerImpl.class);

    private final KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;
    private final PaymentEventsProperties properties;

    public PaymentEventProducerImpl(
            KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate,
            PaymentEventsProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    // AFTER_COMMIT: an event is never sent for a payment that was rolled back.
    // Known gap: if the app stops or Kafka is down right after the commit, the event is lost.
    // The transactional outbox pattern closes that gap.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Override
    public void sendPaymentCreated(PaymentCreatedEvent event) {
        String key = String.valueOf(event.paymentId());

        try {
            kafkaTemplate.send(properties.topic(), key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            logSendFailure(event, ex);
                        } else {
                            log.info("Published PaymentCreated for payment {} (event {}) to {}-{}@{}",
                                    event.paymentId(),
                                    event.eventId(),
                                    result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException ex) {
            // send() itself throws when it can't get broker metadata within max.block.ms.
            logSendFailure(event, ex);
        }
    }

    private static void logSendFailure(PaymentCreatedEvent event, Throwable ex) {
        log.error("Failed to publish PaymentCreated for payment {} (event {})",
                event.paymentId(), event.eventId(), ex);
    }
}
