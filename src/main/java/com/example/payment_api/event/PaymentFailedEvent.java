package com.example.payment_api.event;

import java.time.Instant;
import java.util.UUID;

import com.example.payment_api.entity.Payment;

/** The bank declined the transfer. {@code reason} is the bank's decline reason. */
public record PaymentFailedEvent(
        UUID eventId,
        Instant occurredAt,
        Long paymentId,
        String reason
) implements PaymentEvent {

    public static final String TYPE = "PaymentFailed";

    public static PaymentFailedEvent from(Payment payment, Instant occurredAt) {
        return new PaymentFailedEvent(
                UUID.randomUUID(), occurredAt, payment.getId(), payment.getFailureReason());
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}
