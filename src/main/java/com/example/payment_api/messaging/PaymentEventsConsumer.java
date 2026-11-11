package com.example.payment_api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.example.payment_api.event.PaymentCreatedEvent;
import com.example.payment_api.service.PaymentProcessingService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Processes payment events. Failures are handled by KafkaErrorHandlingConfig: retried with
 * backoff, or sent to the dead-letter topic.
 */
@Component
public class PaymentEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsConsumer.class);

    private final PaymentProcessingService processingService;
    private final JsonMapper jsonMapper;

    public PaymentEventsConsumer(PaymentProcessingService processingService, JsonMapper jsonMapper) {
        this.processingService = processingService;
        this.jsonMapper = jsonMapper;
    }

    // Its own consumer group, so it tracks its own position in the topic.
    // Events for one payment share a partition, so they're processed one at a time, in order.
    @KafkaListener(
            id = "payment-processor",
            groupId = "payment-processor",
            topics = "${payment.events.topic}",
            autoStartup = "${payment.processing.enabled}"
    )
    public void onPaymentEvent(
            @Payload String payload,
            @Header(name = EventHeaders.EVENT_TYPE, required = false) String eventType,
            @Header(name = EventHeaders.EVENT_ID, required = false) String eventId,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        if (eventType == null) {
            throw new NonRetryableEventException(
                    "Missing " + EventHeaders.EVENT_TYPE + " header at partition " + partition + " offset " + offset);
        }

        // This service only acts on new payments; it publishes the other events itself.
        if (!PaymentCreatedEvent.TYPE.equals(eventType)) {
            log.debug("Ignoring {} (event {})", eventType, eventId);
            return;
        }

        PaymentCreatedEvent event = parse(payload, eventId);

        // IDs only: account numbers stay out of the logs.
        log.info("Processing PaymentCreated for payment {} (event {}) from partition {} offset {}",
                event.paymentId(), event.eventId(), partition, offset);

        processingService.process(event);
    }

    private PaymentCreatedEvent parse(String payload, String eventId) {
        try {
            return jsonMapper.readValue(payload, PaymentCreatedEvent.class);
        } catch (JacksonException ex) {
            throw new NonRetryableEventException("Malformed PaymentCreated event " + eventId, ex);
        }
    }
}
