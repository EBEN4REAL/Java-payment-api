package com.example.payment_api.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.payment_api.enums.PaymentStatus;
import com.example.payment_api.exception.IllegalPaymentStateException;

class PaymentTest {

    @Test
    void shouldMoveThroughProcessingToCompleted() {
        Payment payment = newPayment();

        payment.markProcessing();
        payment.markCompleted("TRF-1");

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals("TRF-1", payment.getBankReference());
    }

    @Test
    void shouldMoveThroughProcessingToFailed() {
        Payment payment = newPayment();

        payment.markProcessing();
        payment.markFailed("ACCOUNT_CLOSED");

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("ACCOUNT_CLOSED", payment.getFailureReason());
    }

    @Test
    void shouldNotCompleteAPaymentThatWasNeverClaimed() {
        Payment payment = newPayment();

        IllegalPaymentStateException ex =
                assertThrows(IllegalPaymentStateException.class, () -> payment.markCompleted("TRF-1"));

        assertEquals(PaymentStatus.PENDING, ex.getStatus());
    }

    @Test
    void shouldOnlyAllowEditsWhilePending() {
        Payment payment = newPayment();
        payment.markProcessing();

        assertThrows(IllegalPaymentStateException.class,
                () -> payment.updateDetails("1111111111", "2222222222", BigDecimal.TEN, "NGN"));
        assertThrows(IllegalPaymentStateException.class, payment::markDeleted);
    }

    @Test
    void shouldNotClaimAPaymentTwice() {
        Payment payment = newPayment();
        payment.markProcessing();

        assertThrows(IllegalPaymentStateException.class, payment::markProcessing);
    }

    @Test
    void shouldAllowDeletingAPendingPayment() {
        Payment payment = newPayment();

        payment.markDeleted();

        assertTrue(payment.isDeleted());
    }

    private static Payment newPayment() {
        return new Payment("1234567890", "0987654321", new BigDecimal("5000.00"), "NGN", "key", "hash");
    }
}
