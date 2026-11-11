package com.example.payment_api.config;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.FixedBackOff;

import com.example.payment_api.client.BankRequestRejectedException;
import com.example.payment_api.messaging.NonRetryableEventException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * What happens when a listener throws. Spring Boot applies this error handler to every
 * {@code @KafkaListener}.
 *
 * <ul>
 *   <li>Can never succeed (malformed message, bank rejected the request): straight to the DLT.</li>
 *   <li>Bank circuit breaker open: wait and retry without limit. The bank is known to be
 *       down, so dead-lettering would only create manual work; the event waits in Kafka.</li>
 *   <li>Anything else (bank error, timeout, concurrent edit): exponential backoff,
 *       then the DLT once retries run out.</li>
 * </ul>
 *
 * <p>Retries block the partition, so later events for the same payments wait their turn
 * and stay in order.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            PaymentEventsProperties eventsProperties,
            PaymentProcessingProperties processingProperties) {

        // Keeps the original partition; the record gets headers describing the exception.
        DeadLetterPublishingRecoverer deadLetterRecoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    log.error("Sending record from {}-{}@{} to {}",
                            record.topic(), record.partition(), record.offset(),
                            eventsProperties.deadLetterTopic(), ex);
                    return new TopicPartition(eventsProperties.deadLetterTopic(), record.partition());
                });

        PaymentProcessingProperties.Retry retry = processingProperties.retry();
        ExponentialBackOffWithMaxRetries transientBackOff = new ExponentialBackOffWithMaxRetries(retry.maxRetries());
        transientBackOff.setInitialInterval(retry.initialInterval().toMillis());
        transientBackOff.setMultiplier(retry.multiplier());
        transientBackOff.setMaxInterval(retry.maxInterval().toMillis());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterRecoverer, transientBackOff);
        errorHandler.addNotRetryableExceptions(
                NonRetryableEventException.class,
                BankRequestRejectedException.class);

        FixedBackOff circuitOpenBackOff = new FixedBackOff(
                retry.circuitOpenInterval().toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS);
        // Returning null uses the default (transientBackOff).
        errorHandler.setBackOffFunction((record, ex) ->
                causedBy(ex, CallNotPermittedException.class) ? circuitOpenBackOff : null);

        // Called for every failed attempt, before deciding whether to retry or dead-letter.
        // Without it, retries are silent until the record finally reaches the DLT.
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Attempt {} failed for record {}-{}@{}: {}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(),
                        rootCause(ex).toString()));

        return errorHandler;
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private static boolean causedBy(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
