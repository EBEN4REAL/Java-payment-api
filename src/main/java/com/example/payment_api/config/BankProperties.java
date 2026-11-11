package com.example.payment_api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bank")
public record BankProperties(
        String baseUrl,
        Duration connectTimeout,
        // Also bounds how long a Kafka consumer thread can wait on one transfer.
        Duration readTimeout
) {}
