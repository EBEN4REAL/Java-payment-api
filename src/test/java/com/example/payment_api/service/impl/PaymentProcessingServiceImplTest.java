package com.example.payment_api.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.payment_api.client.BankClient;
import com.example.payment_api.client.BankTransferRequest;
import com.example.payment_api.client.BankTransferResult;
import com.example.payment_api.client.BankUnavailableException;
import com.example.payment_api.entity.Payment;
import com.example.payment_api.enums.PaymentStatus;
import com.example.payment_api.event.PaymentCreatedEvent;
import com.example.payment_api.service.PaymentLifecycleService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingServiceImplTest {

    @Mock
    private PaymentLifecycleService lifecycleService;

    @Mock
    private BankClient bankClient;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private PaymentProcessingServiceImpl processingService;

    // The event's amount differs from the stored payment's on purpose: the stored one must win.
    private final PaymentCreatedEvent event = new PaymentCreatedEvent(
            UUID.randomUUID(), Instant.now(), 42L, "1234567890", "0987654321",
            new BigDecimal("1.00"), "NGN", PaymentStatus.PENDING);

    @BeforeEach
    void setUp() {
        processingService = new PaymentProcessingServiceImpl(lifecycleService, bankClient, meterRegistry);
    }

    @Test
    void shouldSendTheStoredPaymentToTheBankAndRecordTheResult() {
        Payment payment = claimedPayment(42L);
        when(lifecycleService.startProcessing(42L)).thenReturn(Optional.of(payment));
        BankTransferResult accepted = new BankTransferResult.Accepted("TRF-1");
        BankTransferRequest expectedRequest = new BankTransferRequest(
                "payment-42", "1234567890", "0987654321", new BigDecimal("5000.00"), "NGN");
        when(bankClient.transfer(expectedRequest)).thenReturn(accepted);

        processingService.process(event);

        verify(lifecycleService).recordBankResult(42L, accepted);
        assertEquals(1.0, meterRegistry.counter("payments.processed", "outcome", "completed").count());
    }

    @Test
    void shouldNotCallTheBankWhenThereIsNothingToProcess() {
        when(lifecycleService.startProcessing(42L)).thenReturn(Optional.empty());

        processingService.process(event);

        verify(bankClient, never()).transfer(any());
        verify(lifecycleService, never()).recordBankResult(anyLong(), any());
    }

    @Test
    void shouldLeaveThePaymentProcessingWhenTheBankIsUnavailable() {
        when(lifecycleService.startProcessing(42L)).thenReturn(Optional.of(claimedPayment(42L)));
        when(bankClient.transfer(any())).thenThrow(new BankUnavailableException("down", null));

        // Propagates so the Kafka error handler retries; the next attempt resumes the payment.
        assertThrows(BankUnavailableException.class, () -> processingService.process(event));
        verify(lifecycleService, never()).recordBankResult(anyLong(), any());
    }

    private static Payment claimedPayment(Long id) {
        Payment payment = new Payment("1234567890", "0987654321", new BigDecimal("5000.00"), "NGN", "key", "hash");
        ReflectionTestUtils.setField(payment, "id", id);
        payment.markProcessing();
        return payment;
    }
}
