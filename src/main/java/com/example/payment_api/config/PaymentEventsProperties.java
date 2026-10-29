package com.example.payment_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.events")
public record PaymentEventsProperties(
        String topic,
        int partitions,
        short replicationFactor
) {}
