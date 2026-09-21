package com.dp.deviceops.adapter.persistence.jdbc;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Transactional callback outbox. Delivery and retry workers are intentionally outside this adapter task. */
public final class OutboxJdbcRepository {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public OutboxJdbcRepository(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
    }

    public void completeTargetAndAppendEvent(long targetId, String status, OutboxEvent event) {
        try {
            transactions.executeWithoutResult(ignored -> {
                jdbc.sql("INSERT INTO device_ops_outbox (event_id,aggregate_id,event_type,payload,attempt_count,next_attempt_at) VALUES (:id,:aggregateId,:type,:payload,0,:nextAttemptAt)")
                        .param("id", event.eventId()).param("aggregateId", event.aggregateId()).param("type", event.eventType())
                        .param("payload", event.payload()).param("nextAttemptAt", event.nextAttemptAt()).update();
                int changed = jdbc.sql("UPDATE device_ops_collection_target SET status=:status WHERE id=:id")
                        .param("status", status).param("id", targetId).update();
                if (changed != 1) throw new IllegalArgumentException("target does not exist");
            });
        } catch (DuplicateKeyException duplicate) {
            OutboxEvent existing = find(event.eventId()).orElseThrow(() -> duplicate);
            if (!existing.equals(event)) throw new IllegalStateException("event id already belongs to different outbox payload", duplicate);
        }
    }

    public List<OutboxEvent> findPending(Instant now, int limit) {
        return jdbc.sql("SELECT event_id,aggregate_id,event_type,payload,attempt_count,next_attempt_at FROM device_ops_outbox WHERE delivered_at IS NULL AND next_attempt_at <= :now ORDER BY next_attempt_at,event_id LIMIT :limit")
                .param("now", now).param("limit", limit).query((rs, row) -> new OutboxEvent(rs.getString("event_id"), rs.getString("aggregate_id"), rs.getString("event_type"), rs.getString("payload"), rs.getTimestamp("next_attempt_at").toInstant())).list();
    }

    private java.util.Optional<OutboxEvent> find(String eventId) {
        return jdbc.sql("SELECT event_id,aggregate_id,event_type,payload,next_attempt_at FROM device_ops_outbox WHERE event_id=:id")
                .param("id", eventId).query((rs, row) -> new OutboxEvent(rs.getString("event_id"), rs.getString("aggregate_id"), rs.getString("event_type"), rs.getString("payload"), rs.getTimestamp("next_attempt_at").toInstant())).optional();
    }

    public record OutboxEvent(String eventId, String aggregateId, String eventType, String payload, Instant nextAttemptAt) {
        public OutboxEvent { Objects.requireNonNull(eventId); Objects.requireNonNull(aggregateId); Objects.requireNonNull(eventType); Objects.requireNonNull(payload); Objects.requireNonNull(nextAttemptAt); }
    }
}
