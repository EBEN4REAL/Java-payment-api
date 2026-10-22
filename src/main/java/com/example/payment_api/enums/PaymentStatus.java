package com.example.payment_api.enums;

// Every payment starts as PENDING. The chk_payments_status constraint must list the same values.
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}
