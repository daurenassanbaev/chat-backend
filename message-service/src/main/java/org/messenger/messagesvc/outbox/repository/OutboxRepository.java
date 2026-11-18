package org.messenger.messagesvc.outbox.repository;

import org.messenger.messagesvc.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            SELECT * FROM outbox_events WHERE sent = false
            ORDER BY created_at, id LIMIT :batchSize FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockBatch(@Param("batchSize") int batchSize);

    // Retention is measured from creation, as requested; unsent rows are never deleted.
    @Modifying
    @Query(value = """
            DELETE FROM outbox_events WHERE id IN (
                SELECT id FROM outbox_events
                WHERE sent = true AND created_at < CURRENT_TIMESTAMP - INTERVAL '7 days'
                ORDER BY created_at, id LIMIT :batchSize FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int deleteOldSentBatch(@Param("batchSize") int batchSize);
}
