
package com.example.payment_api.service;

import com.example.payment_api.config.PaymentProperties;
import com.example.payment_api.dto.PaymentRequest;
import com.example.payment_api.dto.PaymentResponse;
import com.example.payment_api.exception.PaymentNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {

    @Test
    void shouldCreatePaymentSuccessfully() {
        // Arrange
        PaymentProperties properties =
        new PaymentProperties("NGN", "PENDING");

        PaymentService paymentService = new PaymentService(properties);

        PaymentRequest request = new PaymentRequest(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN"
        );

        // Act
        PaymentResponse response =
                paymentService.createPayment(request);

        // Assert
        assertNotNull(response.id());
        assertEquals("1234567890", response.sourceAccount());
        assertEquals("0987654321", response.destinationAccount());
        assertEquals(new BigDecimal("5000.00"), response.amount());
        assertEquals("NGN", response.currency());
        assertEquals("PENDING", response.status());
        assertNotNull(response.createdAt());
    }

    @Test
    void shouldThrowExceptionWhenPaymentDoesNotExist() {
        // Arrange
        PaymentProperties properties =
        new PaymentProperties("NGN", "PENDING");

        PaymentService paymentService = new PaymentService(properties);

        // Act and Assert
        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.getPayment(999L)
        );
    }


    @Test
    void shouldAssignUniqueIdsToPayments() {
        // Arrange
        PaymentProperties properties =
        new PaymentProperties("NGN", "PENDING");

PaymentService paymentService = new PaymentService(properties);

        PaymentRequest firstRequest = new PaymentRequest(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN"
        );

        PaymentRequest secondRequest = new PaymentRequest(
                "1111111111",
                "2222222222",
                new BigDecimal("2500.00"),
                "NGN"
        );

        // Act
        PaymentResponse firstPayment =
                paymentService.createPayment(firstRequest);

        PaymentResponse secondPayment =
                paymentService.createPayment(secondRequest);

        // Assert
        assertNotNull(firstPayment.id());
        assertNotNull(secondPayment.id());
        assertNotEquals(firstPayment.id(), secondPayment.id());
    }
}