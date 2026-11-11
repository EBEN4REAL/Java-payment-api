package com.example.payment_api.client;

// The bank rejected the request itself (4xx) or answered with something we don't understand.
// Retrying won't help: this is a bug or a contract mismatch to investigate.
public class BankRequestRejectedException extends RuntimeException {

    public BankRequestRejectedException(String message) {
        super(message);
    }

    public BankRequestRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
