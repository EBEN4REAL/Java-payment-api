
package com.example.payment_api.controller;

import com.example.payment_api.dto.PaymentRequest;
import com.example.payment_api.dto.PaymentResponse;
import com.example.payment_api.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    @Test
    void shouldCreatePaymentSuccessfully() {
        // Arrange
        PaymentRequest request = new PaymentRequest(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN"
        );

        PaymentResponse expectedResponse = new PaymentResponse(
                1L,
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN",
                "PENDING",
                Instant.now()
        );

        when(paymentService.createPayment(request, "key-1"))
                .thenReturn(expectedResponse);

        // Act
        PaymentResponse actualResponse =
                paymentController.createPayment("key-1", request);

        // Assert
        assertEquals(1L, actualResponse.id());
        assertEquals("PENDING", actualResponse.status());

        verify(paymentService).createPayment(request, "key-1");
    }

    @Test
    void shouldUpdatePaymentSuccessfully() {
        // Arrange
        PaymentRequest request = new PaymentRequest(
                "1111111111",
                "2222222222",
                new BigDecimal("9000.00"),
                "NGN"
        );

        PaymentResponse expectedResponse = new PaymentResponse(
                1L,
                "1111111111",
                "2222222222",
                new BigDecimal("9000.00"),
                "NGN",
                "PENDING",
                Instant.now()
        );

        when(paymentService.updatePayment(1L, request))
                .thenReturn(expectedResponse);

        // Act
        PaymentResponse actualResponse =
                paymentController.updatePayment(1L, request);

        // Assert
        assertEquals(new BigDecimal("9000.00"), actualResponse.amount());

        verify(paymentService).updatePayment(1L, request);
    }

    @Test
    void shouldDeletePaymentSuccessfully() {
        // Act
        paymentController.deletePayment(1L);

        // Assert
        verify(paymentService).deletePayment(1L);
    }

    @Test
    void shouldReturnAllPayments() {
        // Arrange
        PaymentResponse payment = new PaymentResponse(
                1L,
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN",
                "PENDING",
                Instant.now()
        );

        when(paymentService.getAllPayments())
                .thenReturn(List.of(payment));

        // Act
        List<PaymentResponse> actualResponse =
                paymentController.getAllPayments();

        // Assert
        assertEquals(1, actualResponse.size());
        assertEquals(1L, actualResponse.get(0).id());
    }
}
