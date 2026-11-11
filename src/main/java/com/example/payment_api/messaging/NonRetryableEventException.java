package com.example.payment_api.messaging;

// The message can never be processed (e.g. malformed or missing headers): send it straight to the DLT.
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
