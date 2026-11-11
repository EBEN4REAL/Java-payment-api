
package com.example.payment_api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.payment_api.enums.PaymentStatus;

public record PaymentResponse(
        Long id,
        String sourceAccount,
        String destinationAccount,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        // Set once the bank accepts the transfer.
        String bankReference,
        // Set if the bank declines the transfer.
        String failureReason
) {}