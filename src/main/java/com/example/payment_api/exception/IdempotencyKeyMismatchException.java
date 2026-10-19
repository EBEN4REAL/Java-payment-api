package com.example.payment_api.exception;

public class IdempotencyKeyMismatchException extends RuntimeException {

    public IdempotencyKeyMismatchException(String idempotencyKey) {
        super("Idempotency key " + idempotencyKey
                + " was already used with a different request body");
    }
}
