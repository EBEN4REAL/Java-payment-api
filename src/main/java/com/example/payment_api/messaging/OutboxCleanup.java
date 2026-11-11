package com.example.payment_api.messaging;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.payment_api.config.OutboxProperties;
import com.example.payment_api.repository.OutboxEventRepository;

// Deletes published outbox events after the retention period. Unpublished events are never deleted.
@Component
public class OutboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanup.class);
    private static final int DELETE_BATCH_SIZE = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public OutboxCleanup(
            OutboxEventRepository outboxEventRepository,
            OutboxProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${payment.outbox.cleanup-cron}")
    public void deleteOldPublishedEvents() {
        Instant cutoff = clock.instant().minus(properties.retention());
        int total = 0;
        int deleted;
        do {
            // One short transaction per batch, so no long-held locks.
            deleted = transactionTemplate.execute(
                    status -> outboxEventRepository.deletePublishedBefore(cutoff, DELETE_BATCH_SIZE));
            total += deleted;
        } while (deleted == DELETE_BATCH_SIZE);

        if (total > 0) {
            log.info("Deleted {} outbox events published before {}", total, cutoff);
        }
    }
}
