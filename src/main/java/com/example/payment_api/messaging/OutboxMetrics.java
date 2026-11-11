package com.example.payment_api.messaging;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.example.payment_api.repository.OutboxEventRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Outbox backlog gauges, read from the database when metrics are scraped.
 *
 * <p>A growing count or age means events aren't reaching Kafka (broker down, relay stuck):
 * alert on {@code outbox.events.oldest.age} rather than the count, as the count can be
 * briefly high during a burst of payments.
 */
@Component
public class OutboxMetrics {

    public OutboxMetrics(OutboxEventRepository outboxEventRepository, Clock clock, MeterRegistry meterRegistry) {
        Gauge.builder("outbox.events.pending", outboxEventRepository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events not yet published to Kafka")
                .register(meterRegistry);

        Gauge.builder("outbox.events.oldest.age", outboxEventRepository, repository ->
                        repository.findFirstByPublishedAtIsNullOrderByIdAsc()
                                .map(event -> Duration.between(event.getCreatedAt(), clock.instant()).toMillis() / 1000.0)
                                .orElse(0.0))
                .description("Age of the oldest unpublished outbox event")
                .baseUnit("seconds")
                .register(meterRegistry);
    }
}
