package com.example.payment_api.client;

import java.math.BigDecimal;

// reference is also sent as the Idempotency-Key, so a retried transfer is never applied twice.
public record BankTransferRequest(
        String reference,
        String sourceAccount,
        String destinationAccount,
        BigDecimal amount,
        String currency
) {}
