package com.example.payment_api.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.payment_api.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Locks the oldest unpublished events until the transaction ends.
     *
     * <p>SKIP LOCKED lets several app instances relay at once without waiting on, or
     * double-publishing, each other's rows. Run it under READ COMMITTED so it doesn't
     * take gap locks that would block new payments from inserting outbox rows.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextUnpublished(@Param("limit") int limit);

    // Batched so one cleanup run never holds a huge delete open.
    @Modifying
    @Query(value = "DELETE FROM outbox_events WHERE published_at < :cutoff LIMIT :limit", nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    long countByPublishedAtIsNull();

    Optional<OutboxEvent> findFirstByPublishedAtIsNullOrderByIdAsc();
}
