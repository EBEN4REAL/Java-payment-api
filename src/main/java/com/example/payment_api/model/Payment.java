
package com.example.payment_api.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Payment(
        Long id,
        String sourceAccount,
        String destinationAccount,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt
) {}