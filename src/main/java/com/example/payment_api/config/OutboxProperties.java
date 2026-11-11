package com.example.payment_api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.outbox")
public record OutboxProperties(
        // How long the relay waits between polls once it has caught up.
        Duration pollInterval,
        int batchSize,
        // Upper bound on waiting for Kafka to acknowledge one batch.
        Duration sendTimeout,
        // Published events are kept this long for troubleshooting, then deleted.
        Duration retention,
        String cleanupCron
) {}
