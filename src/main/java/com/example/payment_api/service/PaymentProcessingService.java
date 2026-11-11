package com.example.payment_api.service;

import com.example.payment_api.event.PaymentCreatedEvent;

public interface PaymentProcessingService {

    /**
     * Sends a newly created payment to the bank and records the outcome.
     *
     * <p>Safe to call more than once for the same event: duplicates are skipped and an
     * interrupted payment resumes. Exceptions mean "try again later" or "can't process";
     * the Kafka error handler decides which.
     */
    void process(PaymentCreatedEvent event);
}
