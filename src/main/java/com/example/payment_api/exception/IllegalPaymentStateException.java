package com.example.payment_api.exception;

import com.example.payment_api.enums.PaymentStatus;

// The payment's current status doesn't allow the requested action, e.g. editing a completed payment.
public class IllegalPaymentStateException extends RuntimeException {

    private final PaymentStatus status;

    public IllegalPaymentStateException(Long id, PaymentStatus status, String action) {
        super("Cannot " + action + " payment " + id + " because it is " + status);
        this.status = status;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
