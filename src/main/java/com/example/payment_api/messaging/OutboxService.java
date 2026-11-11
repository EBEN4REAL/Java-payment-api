package com.example.payment_api.messaging;

import com.example.payment_api.event.PaymentEvent;

public interface OutboxService {

    /**
     * Stores the event so OutboxRelay publishes it to the payment events topic.
     *
     * <p>Must be called inside the transaction that makes the change the event describes:
     * the event is then saved if and only if that change commits.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException if no transaction is active
     */
    void enqueue(PaymentEvent event);
}
