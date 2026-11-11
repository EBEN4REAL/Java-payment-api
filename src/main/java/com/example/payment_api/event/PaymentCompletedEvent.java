package com.example.payment_api.event;

import java.time.Instant;
import java.util.UUID;

import com.example.payment_api.entity.Payment;

/** The bank accepted the transfer. {@code bankReference} is the bank's transfer ID. */
public record PaymentCompletedEvent(
        UUID eventId,
        Instant occurredAt,
        Long paymentId,
        String bankReference
) implements PaymentEvent {

    public static final String TYPE = "PaymentCompleted";

    public static PaymentCompletedEvent from(Payment payment, Instant occurredAt) {
        return new PaymentCompletedEvent(
                UUID.randomUUID(), occurredAt, payment.getId(), payment.getBankReference());
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}
