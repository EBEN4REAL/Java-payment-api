package com.example.payment_api.service.impl;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.payment_api.client.BankClient;
import com.example.payment_api.client.BankTransferRequest;
import com.example.payment_api.client.BankTransferResult;
import com.example.payment_api.entity.Payment;
import com.example.payment_api.event.PaymentCreatedEvent;
import com.example.payment_api.service.PaymentLifecycleService;
import com.example.payment_api.service.PaymentProcessingService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Claim, call the bank, record the result.
 *
 * <p>Deliberately not transactional: the bank call happens between two short transactions,
 * so no database connection or row lock is held while waiting on the network.
 */
@Service
public class PaymentProcessingServiceImpl implements PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingServiceImpl.class);

    private final PaymentLifecycleService lifecycleService;
    private final BankClient bankClient;
    private final Counter completedCounter;
    private final Counter failedCounter;

    public PaymentProcessingServiceImpl(
            PaymentLifecycleService lifecycleService,
            BankClient bankClient,
            MeterRegistry meterRegistry) {
        this.lifecycleService = lifecycleService;
        this.bankClient = bankClient;
        this.completedCounter = processedCounter(meterRegistry, "completed");
        this.failedCounter = processedCounter(meterRegistry, "failed");
    }

    @Override
    public void process(PaymentCreatedEvent event) {
        Optional<Payment> claimed = lifecycleService.startProcessing(event.paymentId());
        if (claimed.isEmpty()) {
            return;
        }

        // Use the stored payment, not the event: it's the source of truth, and it can't
        // change any more now that it's PROCESSING.
        Payment payment = claimed.get();
        BankTransferResult result = bankClient.transfer(new BankTransferRequest(
                bankReference(payment),
                payment.getSourceAccount(),
                payment.getDestinationAccount(),
                payment.getAmount(),
                payment.getCurrency()
        ));

        lifecycleService.recordBankResult(payment.getId(), result);

        switch (result) {
            case BankTransferResult.Accepted accepted -> {
                completedCounter.increment();
                log.info("Payment {} completed, bank transfer {}", payment.getId(), accepted.transferId());
            }
            case BankTransferResult.Declined declined -> {
                failedCounter.increment();
                log.info("Payment {} declined by bank: {}", payment.getId(), declined.reason());
            }
        }
    }

    private static Counter processedCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder("payments.processed")
                .description("Payments the bank has answered, by outcome")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    // Stable per payment, so every retry asks the bank for the same transfer.
    static String bankReference(Payment payment) {
        return "payment-" + payment.getId();
    }
}
