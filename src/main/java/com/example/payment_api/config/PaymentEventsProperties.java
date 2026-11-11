package com.example.payment_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.events")
public record PaymentEventsProperties(
        String topic,
        // Events that failed processing for good, with the error in the message headers.
        String deadLetterTopic,
        int partitions,
        short replicationFactor
) {}
