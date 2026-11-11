package com.example.payment_api.service;

import java.util.Optional;

import com.example.payment_api.client.BankTransferResult;
import com.example.payment_api.entity.Payment;

// Status changes made by the payment processor. Each method is one short transaction.
public interface PaymentLifecycleService {

    /**
     * Claims the payment for processing: PENDING becomes PROCESSING.
     *
     * <p>Returns the payment to send to the bank, or empty when there's nothing to do
     * (already completed or failed, deleted, or unknown). A payment that is already
     * PROCESSING is returned again, so processing resumes after a crash.
     *
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException if the payment
     *         was edited at the same moment; safe to retry
     */
    Optional<Payment> startProcessing(Long paymentId);

    /**
     * Stores the bank's answer (COMPLETED or FAILED) and adds PaymentCompleted or
     * PaymentFailed to the outbox in the same transaction.
     */
    void recordBankResult(Long paymentId, BankTransferResult result);
}
