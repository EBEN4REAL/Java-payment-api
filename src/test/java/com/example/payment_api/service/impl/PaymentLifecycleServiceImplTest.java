package com.example.payment_api.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.payment_api.client.BankTransferResult;
import com.example.payment_api.entity.Payment;
import com.example.payment_api.enums.PaymentStatus;
import com.example.payment_api.event.PaymentCompletedEvent;
import com.example.payment_api.event.PaymentEvent;
import com.example.payment_api.event.PaymentFailedEvent;
import com.example.payment_api.messaging.OutboxService;
import com.example.payment_api.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentLifecycleServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxService outboxService;

    private PaymentLifecycleServiceImpl lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new PaymentLifecycleServiceImpl(
                paymentRepository, outboxService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldClaimAPendingPayment() {
        Payment payment = payment(42L);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        Optional<Payment> claimed = lifecycleService.startProcessing(42L);

        assertTrue(claimed.isPresent());
        assertEquals(PaymentStatus.PROCESSING, claimed.get().getStatus());
        verify(paymentRepository).saveAndFlush(payment);
    }

    @Test
    void shouldResumeAPaymentThatIsAlreadyProcessing() {
        Payment payment = payment(42L);
        payment.markProcessing();
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

        Optional<Payment> claimed = lifecycleService.startProcessing(42L);

        assertTrue(claimed.isPresent());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldSkipAPaymentThatIsAlreadyFinished() {
        Payment payment = payment(42L);
        payment.markProcessing();
        payment.markCompleted("TRF-1");
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

        assertTrue(lifecycleService.startProcessing(42L).isEmpty());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldSkipADeletedPayment() {
        Payment payment = payment(42L);
        payment.markDeleted();
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

        assertTrue(lifecycleService.startProcessing(42L).isEmpty());
    }

    @Test
    void shouldSkipAnUnknownPayment() {
        when(paymentRepository.findById(42L)).thenReturn(Optional.empty());

        assertTrue(lifecycleService.startProcessing(42L).isEmpty());
    }

    @Test
    void shouldCompleteThePaymentAndEnqueuePaymentCompleted() {
        Payment payment = payment(42L);
        payment.markProcessing();
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

        lifecycleService.recordBankResult(42L, new BankTransferResult.Accepted("TRF-1"));

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals("TRF-1", payment.getBankReference());

        PaymentCompletedEvent event = (PaymentCompletedEvent) enqueuedEvent();
        assertEquals(42L, event.paymentId());
        assertEquals("TRF-1", event.bankReference());
        assertEquals(NOW, event.occurredAt());
    }

    @Test
    void shouldFailThePaymentAndEnqueuePaymentFailed() {
        Payment payment = payment(42L);
        payment.markProcessing();
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

        lifecycleService.recordBankResult(42L, new BankTransferResult.Declined("ACCOUNT_CLOSED"));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        PaymentFailedEvent event = (PaymentFailedEvent) enqueuedEvent();
        assertEquals("ACCOUNT_CLOSED", event.reason());
    }

    private PaymentEvent enqueuedEvent() {
        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outboxService).enqueue(captor.capture());
        return captor.getValue();
    }

    private static Payment payment(Long id) {
        Payment payment = new Payment("1234567890", "0987654321", new BigDecimal("5000.00"), "NGN", "key", "hash");
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
