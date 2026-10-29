package com.example.payment_api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.payment_api.entity.Payment;
import com.example.payment_api.enums.PaymentStatus;

/**
 * Published to the payment events topic after a payment is saved.
 *
 * <p>This is a contract with other services: add fields freely, but don't rename or
 * remove them without versioning the event. Kafka can deliver an event more than once,
 * so consumers should use {@code eventId} to ignore duplicates.
 */
public record PaymentCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        Long paymentId,
        String sourceAccount,
        String destinationAccount,
        BigDecimal amount,
        String currency,
        PaymentStatus status
) {

    public static PaymentCreatedEvent from(Payment payment) {
        return new PaymentCreatedEvent(
                UUID.randomUUID(),
                payment.getCreatedAt(),
                payment.getId(),
                payment.getSourceAccount(),
                payment.getDestinationAccount(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus()
        );
    }
}
