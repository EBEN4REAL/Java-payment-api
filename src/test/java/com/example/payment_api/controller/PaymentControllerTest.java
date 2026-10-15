
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

        when(paymentService.createPayment(request))
                .thenReturn(expectedResponse);

        // Act
        PaymentResponse actualResponse =
                paymentController.createPayment(request);

        // Assert
        assertEquals(1L, actualResponse.id());
        assertEquals("PENDING", actualResponse.status());

        verify(paymentService).createPayment(request);
    }
}