package com.example.payment_api.exception;

public class InvalidCredentialsException extends RuntimeException {

    // Same message for unknown email and wrong password, so attackers can't tell which.
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
