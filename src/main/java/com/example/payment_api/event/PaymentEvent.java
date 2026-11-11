package com.example.payment_api.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * An event about one payment, published to the payment events topic keyed by payment ID,
 * so all events for a payment are delivered in order.
 *
 * <p>The event type travels in the {@code eventType} Kafka header, never as a Java class
 * name. Kafka can deliver an event more than once: consumers use {@code eventId} or the
 * payment's state to ignore duplicates.
 */
public sealed interface PaymentEvent
        permits PaymentCreatedEvent, PaymentCompletedEvent, PaymentFailedEvent {

    UUID eventId();

    Instant occurredAt();

    Long paymentId();

    // Part of the contract: renaming a type breaks consumers.
    @JsonIgnore
    String eventType();
}
