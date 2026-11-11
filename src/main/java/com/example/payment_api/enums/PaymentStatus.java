package com.example.payment_api.enums;

// PENDING -> PROCESSING -> COMPLETED or FAILED. Payment enforces the allowed moves.
// The chk_payments_status constraint must list the same values.
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
