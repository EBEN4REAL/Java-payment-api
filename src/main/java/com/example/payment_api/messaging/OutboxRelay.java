package com.example.payment_api.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.payment_api.config.OutboxProperties;
import com.example.payment_api.entity.OutboxEvent;
import com.example.payment_api.repository.OutboxEventRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes outbox events to Kafka, oldest first, and marks them as published.
 *
 * <p>Delivery is at least once: if the app stops after Kafka acknowledges an event but
 * before the row is marked, the event is sent again on the next run. Consumers must
 * ignore duplicates. With several instances, SKIP LOCKED splits the work between them,
 * so events for different payments can be published out of order.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {};

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final TracePropagation tracePropagation;
    private final TransactionTemplate transactionTemplate;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxRelay(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties,
            JsonMapper jsonMapper,
            Clock clock,
            TracePropagation tracePropagation,
            MeterRegistry meterRegistry,
            PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.tracePropagation = tracePropagation;
        this.publishedCounter = Counter.builder("outbox.events.published")
                .description("Outbox events published to Kafka")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("outbox.events.publish.failures")
                .description("Failed attempts to publish an outbox event; the event is retried")
                .register(meterRegistry);

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // No gap locks, so the relay never blocks new payments from writing outbox rows.
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    // fixedDelay: the next run starts only after this one finishes, so runs never overlap.
    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval}")
    public void relayPendingEvents() {
        int published;
        do {
            published = transactionTemplate.execute(status -> publishNextBatch());
        } while (published == properties.batchSize()); // A full batch means more may be waiting.
    }

    /**
     * Publishes one batch inside the caller's transaction and returns how many succeeded.
     * Row locks are held until that transaction commits.
     */
    int publishNextBatch() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextUnpublished(properties.batchSize());
        if (batch.isEmpty()) {
            return 0;
        }

        // Send everything first, then wait: the producer batches the records together.
        List<CompletableFuture<SendResult<String, String>>> sends = new ArrayList<>();
        for (OutboxEvent event : batch) {
            try {
                // In the trace of the request that wrote the event; Kafka's producer span joins it.
                sends.add(tracePropagation.callInContext(
                        savedTraceContext(event), "outbox publish", () -> kafkaTemplate.send(toRecord(event))));
            } catch (RuntimeException ex) {
                // send() throws when Kafka is unreachable. Don't wait on the rest of the batch too.
                event.recordFailure(ex.getMessage());
                failedCounter.increment();
                log.warn("Kafka unavailable, outbox event {} ({}) will be retried",
                        event.getEventId(), event.getEventType(), ex);
                break;
            }
        }

        Instant now = clock.instant();
        int published = 0;
        for (int i = 0; i < sends.size(); i++) {
            OutboxEvent event = batch.get(i);
            try {
                sends.get(i).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(now);
                publishedCounter.increment();
                published++;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                event.recordFailure("Interrupted while waiting for Kafka");
                break;
            } catch (Exception ex) {
                event.recordFailure(ex.getMessage());
                failedCounter.increment();
                log.warn("Failed to publish outbox event {} ({}), attempt {}",
                        event.getEventId(), event.getEventType(), event.getAttempts(), ex);
            }
        }

        if (published > 0) {
            log.debug("Published {} outbox events", published);
        }
        return published;
    }

    private ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());

        // The trace header (traceparent) is added by KafkaTemplate's observation, not copied here.
        addHeader(record, EventHeaders.EVENT_ID, event.getEventId());
        addHeader(record, EventHeaders.EVENT_TYPE, event.getEventType());
        return record;
    }

    private Map<String, String> savedTraceContext(OutboxEvent event) {
        return event.getHeaders() == null ? Map.of() : jsonMapper.readValue(event.getHeaders(), HEADERS_TYPE);
    }

    private static void addHeader(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
    }
}
