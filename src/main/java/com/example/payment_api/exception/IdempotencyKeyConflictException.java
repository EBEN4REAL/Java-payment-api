package com.example.payment_api.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("A request with idempotency key " + idempotencyKey
                + " is already being processed. Retry the request.");
    }
}
