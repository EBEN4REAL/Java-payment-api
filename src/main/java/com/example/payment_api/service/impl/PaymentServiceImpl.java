
package com.example.payment_api.service.impl;

import com.example.payment_api.service.PaymentService;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment_api.config.PaymentProperties;
import com.example.payment_api.dto.PaymentRequest;
import com.example.payment_api.dto.PaymentResponse;
import com.example.payment_api.exception.IdempotencyKeyConflictException;
import com.example.payment_api.exception.IdempotencyKeyMismatchException;
import com.example.payment_api.exception.InvalidIdempotencyKeyException;
import com.example.payment_api.exception.PaymentNotFoundException;
import com.example.payment_api.entity.Payment;
import com.example.payment_api.repository.PaymentRepository;
import com.example.payment_api.util.ConstraintViolations;
import com.example.payment_api.util.Hashing;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentProperties paymentProperties;
    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(
            PaymentProperties paymentProperties,
            PaymentRepository paymentRepository) {
        this.paymentProperties = paymentProperties;
        this.paymentRepository = paymentRepository;
    }

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final String IDEMPOTENCY_KEY_CONSTRAINT = "uk_payments_idempotency_key";

    @Transactional
    @Override
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        String requestHash = hashRequest(request);

        // A retry with a key we've already seen returns the original payment.
        Optional<Payment> existingPayment =
                paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();

            if (!payment.getIdempotencyRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyMismatchException(idempotencyKey);
            }

            return toResponse(payment);
        }

        Payment payment = new Payment(
                request.sourceAccount(),
                request.destinationAccount(),
                request.amount(),
                request.currency(),
                paymentProperties.defaultStatus(),
                idempotencyKey,
                requestHash
        );

        try {
            Payment savedPayment = paymentRepository.saveAndFlush(payment);

            return toResponse(savedPayment);
        } catch (DataIntegrityViolationException ex) {
            // Another request with the same key was saved between our lookup and insert.
            if (ConstraintViolations.isViolationOf(ex, IDEMPOTENCY_KEY_CONSTRAINT)) {
                throw new IdempotencyKeyConflictException(idempotencyKey);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "payments", key = "#id")
    @Override
    public PaymentResponse getPayment(Long id) {
        Payment payment = paymentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        return toResponse(payment);
    }

    // @CachePut stores the updated payment in Redis so GET doesn't return stale data.
    @Transactional
    @CachePut(cacheNames = "payments", key = "#id")
    @Override
    public PaymentResponse updatePayment(Long id, PaymentRequest request) {
        Payment payment = paymentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        payment.updateDetails(
                request.sourceAccount(),
                request.destinationAccount(),
                request.amount(),
                request.currency()
        );

        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        return toResponse(savedPayment);
    }

    @Transactional
    @CacheEvict(cacheNames = "payments", key = "#id")
    @Override
    public void deletePayment(Long id) {
        Payment payment = paymentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        // Soft delete: keep the row, just flag it as deleted.
        payment.markDeleted();
        paymentRepository.saveAndFlush(payment);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAllByDeletedFalseOrderByIdAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key header must not be blank");
        }

        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key header must be at most "
                            + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }

    // stripTrailingZeros makes 5000 and 5000.00 hash the same.
    private String hashRequest(PaymentRequest request) {
        String canonical = String.join("|",
                request.sourceAccount(),
                request.destinationAccount(),
                request.amount().stripTrailingZeros().toPlainString(),
                request.currency());

        return Hashing.sha256Hex(canonical);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getSourceAccount(),
                payment.getDestinationAccount(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }
}
