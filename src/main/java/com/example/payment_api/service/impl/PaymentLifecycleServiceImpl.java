package com.example.payment_api.service.impl;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment_api.client.BankTransferResult;
import com.example.payment_api.entity.Payment;
import com.example.payment_api.event.PaymentCompletedEvent;
import com.example.payment_api.event.PaymentFailedEvent;
import com.example.payment_api.exception.PaymentNotFoundException;
import com.example.payment_api.messaging.OutboxService;
import com.example.payment_api.repository.PaymentRepository;
import com.example.payment_api.service.PaymentLifecycleService;

// The payments cache is transaction-aware, so evictions happen only after commit.
@Service
public class PaymentLifecycleServiceImpl implements PaymentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(PaymentLifecycleServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    public PaymentLifecycleServiceImpl(
            PaymentRepository paymentRepository,
            OutboxService outboxService,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Transactional
    @CacheEvict(cacheNames = "payments", key = "#paymentId")
    @Override
    public Optional<Payment> startProcessing(Long paymentId) {
        Optional<Payment> found = paymentRepository.findById(paymentId);
        if (found.isEmpty()) {
            log.warn("Skipping payment {}: it doesn't exist", paymentId);
            return Optional.empty();
        }

        Payment payment = found.get();
        if (payment.isDeleted()) {
            log.info("Skipping payment {}: it was deleted before processing", paymentId);
            return Optional.empty();
        }

        return switch (payment.getStatus()) {
            case PENDING -> {
                payment.markProcessing();
                // Flush now so the version check runs here. This transaction commits before the bank
                // call anyway, but if this ever runs inside a larger one, a concurrent edit must
                // still fail before the money moves, not at the end.
                yield Optional.of(paymentRepository.saveAndFlush(payment));
            }
            case PROCESSING -> {
                log.info("Resuming payment {}: it was already being processed", paymentId);
                yield Optional.of(payment);
            }
            case COMPLETED, FAILED -> {
                log.info("Skipping payment {}: already {}", paymentId, payment.getStatus());
                yield Optional.empty();
            }
        };
    }

    @Transactional
    @CacheEvict(cacheNames = "payments", key = "#paymentId")
    @Override
    public void recordBankResult(Long paymentId, BankTransferResult result) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        switch (result) {
            case BankTransferResult.Accepted accepted -> {
                payment.markCompleted(accepted.transferId());
                outboxService.enqueue(PaymentCompletedEvent.from(payment, clock.instant()));
            }
            case BankTransferResult.Declined declined -> {
                payment.markFailed(declined.reason());
                outboxService.enqueue(PaymentFailedEvent.from(payment, clock.instant()));
            }
        }
        paymentRepository.saveAndFlush(payment);
    }
}
