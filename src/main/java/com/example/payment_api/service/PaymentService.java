package com.example.payment_api.service;

import java.util.List;

import com.example.payment_api.dto.PaymentRequest;
import com.example.payment_api.dto.PaymentResponse;

public interface PaymentService {

    /**
     * Creates a payment, or returns the existing one if this idempotency key was already used
     * with the same request.
     *
     * @throws com.example.payment_api.exception.InvalidIdempotencyKeyException if the key is blank or too long
     * @throws com.example.payment_api.exception.IdempotencyKeyMismatchException if the key was used with a different request
     * @throws com.example.payment_api.exception.IdempotencyKeyConflictException if another request with the key is in progress
     */
    PaymentResponse createPayment(PaymentRequest request, String idempotencyKey);

    /**
     * @throws com.example.payment_api.exception.PaymentNotFoundException if the payment doesn't exist or was deleted
     */
    PaymentResponse getPayment(Long id);

    /**
     * Replaces the payment's accounts, amount and currency. Status and creation time are kept.
     *
     * @throws com.example.payment_api.exception.PaymentNotFoundException if the payment doesn't exist or was deleted
     */
    PaymentResponse updatePayment(Long id, PaymentRequest request);

    /**
     * Soft-deletes the payment: the row is kept and flagged as deleted.
     *
     * @throws com.example.payment_api.exception.PaymentNotFoundException if the payment doesn't exist or was already deleted
     */
    void deletePayment(Long id);

    /** Returns all payments that haven't been deleted, oldest first. */
    List<PaymentResponse> getAllPayments();
}
