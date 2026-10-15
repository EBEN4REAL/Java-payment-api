
package com.example.payment_api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentRequest(

        @NotBlank(message = "Source account is required")
        @Pattern(
                regexp = "\\d{10}",
                message = "Source account must contain 10 digits"
        )
        String sourceAccount,

        @NotBlank(message = "Destination account is required")
        @Pattern(
                regexp = "\\d{10}",
                message = "Destination account must contain 10 digits"
        )
        String destinationAccount,

        @NotNull(message = "Amount is required")
        @DecimalMin(
                value = "0.00",
                inclusive = false,
                message = "Amount must be greater than zero"
        )
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Pattern(
                regexp = "NGN",
                message = "Only NGN is supported"
        )
        String currency
) {}