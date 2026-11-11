package com.example.payment_api.client;

// The bank couldn't be reached or failed (timeout, 5xx). Safe to retry: transfers are idempotent.
// Counts as a failure for the bank circuit breaker.
public class BankUnavailableException extends RuntimeException {

    public BankUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
