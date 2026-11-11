package com.example.payment_api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.processing")
public record PaymentProcessingProperties(
        // Starts the payment processor consumer. Off in tests that edit payments directly.
        boolean enabled,
        Retry retry
) {

    /**
     * Retries for transient failures (bank down, concurrent edit): exponential backoff,
     * then the dead-letter topic. Retries block the partition, so events for a payment
     * stay in order; the total wait must stay well under max.poll.interval.ms (5 minutes).
     */
    public record Retry(
            Duration initialInterval,
            double multiplier,
            Duration maxInterval,
            int maxRetries,
            // While the bank circuit breaker is open: wait this long and retry, without limit.
            Duration circuitOpenInterval
    ) {}
}
