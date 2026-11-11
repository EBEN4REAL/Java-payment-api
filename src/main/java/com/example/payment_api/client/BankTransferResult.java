package com.example.payment_api.client;

// The bank's answer to a transfer. A decline is a normal business outcome, not an error.
public sealed interface BankTransferResult {

    record Accepted(String transferId) implements BankTransferResult {}

    record Declined(String reason) implements BankTransferResult {}
}
