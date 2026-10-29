package com.example.payment_api.messaging;

import com.example.payment_api.event.PaymentCreatedEvent;

public interface PaymentEventProducer {

    /**
     * Sends the event to the payment events topic, keyed by payment ID.
     *
     * <p>Runs only after the transaction that saved the payment commits. Never throws:
     * the payment is already saved, so a failed send is logged instead of failing the request.
     */
    void sendPaymentCreated(PaymentCreatedEvent event);
}
