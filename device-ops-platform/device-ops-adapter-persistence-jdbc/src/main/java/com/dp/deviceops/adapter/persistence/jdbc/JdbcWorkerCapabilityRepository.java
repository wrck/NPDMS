package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.WorkerCapabilityRepository;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JdbcWorkerCapabilityRepository implements WorkerCapabilityRepository {

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public JdbcWorkerCapabilityRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void heartbeat(String workerId, Set<WorkerCapability> capabilities, Instant expiresAt) {
        transactions.executeWithoutResult(status -> {
            int updated = jdbc.sql("update device_ops_parser_worker set last_heartbeat_at=:now," +
                            "heartbeat_expires_at=:expires where worker_id=:worker")
                    .param("now", clock.instant()).param("expires", expiresAt).param("worker", workerId).update();
            if (updated == 0) {
                jdbc.sql("insert into device_ops_parser_worker(worker_id,last_heartbeat_at,heartbeat_expires_at) " +
                                "values(:worker,:now,:expires)")
                        .param("worker", workerId).param("now", clock.instant()).param("expires", expiresAt).update();
            }
            jdbc.sql("delete from device_ops_parser_worker_capability where worker_id=:worker")
                    .param("worker", workerId).update();
            for (WorkerCapability capability : Set.copyOf(capabilities)) {
                jdbc.sql("insert into device_ops_parser_worker_capability(worker_id,engine_version,extension_id," +
                                "extension_version) values(:worker,:engine,:extension,:version)")
                        .param("worker", workerId).param("engine", capability.engineVersion())
                        .param("extension", capability.extensionId() == null ? "" : capability.extensionId())
                        .param("version", capability.extensionVersion() == null ? "" : capability.extensionVersion())
                        .update();
            }
        });
    }

    @Override
    public long countAvailable(ParserCoordinate coordinate, Instant now) {
        return jdbc.sql("""
                select count(distinct w.worker_id) from device_ops_parser_worker w
                join device_ops_parser_worker_capability c on c.worker_id=w.worker_id
                where w.heartbeat_expires_at>:now and c.engine_version=:engine
                  and c.extension_id=:extension and c.extension_version=:version
                """).param("now", now).param("engine", coordinate.engineVersion())
                .param("extension", coordinate.extensionId() == null ? "" : coordinate.extensionId())
                .param("version", coordinate.extensionVersion() == null ? "" : coordinate.extensionVersion())
                .query(Long.class).single();
    }

    @Override
    public List<WorkerHeartbeat> listActive(Instant now) {
        List<Map<String, Object>> rows = jdbc.sql("""
                select w.worker_id,w.last_heartbeat_at,w.heartbeat_expires_at,
                       c.engine_version,c.extension_id,c.extension_version
                from device_ops_parser_worker w join device_ops_parser_worker_capability c on c.worker_id=w.worker_id
                where w.heartbeat_expires_at>:now order by w.worker_id,c.engine_version,c.extension_id,c.extension_version
                """).param("now", now).query().listOfRows();
        Map<String, MutableHeartbeat> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            MutableHeartbeat heartbeat = grouped.computeIfAbsent((String) row.get("WORKER_ID"), key ->
                    new MutableHeartbeat(key, ParserJdbcJson.instant(row.get("LAST_HEARTBEAT_AT")),
                            ParserJdbcJson.instant(row.get("HEARTBEAT_EXPIRES_AT"))));
            String extension = (String) row.get("EXTENSION_ID");
            heartbeat.capabilities.add(new WorkerCapability((String) row.get("ENGINE_VERSION"),
                    extension.isEmpty() ? null : extension,
                    ((String) row.get("EXTENSION_VERSION")).isEmpty() ? null : (String) row.get("EXTENSION_VERSION")));
        }
        return grouped.values().stream().map(item -> new WorkerHeartbeat(item.workerId,
                item.capabilities, item.lastHeartbeatAt, item.expiresAt)).toList();
    }

    @Override
    public int reconcileWaiting(Instant now, int limit) {
        List<Map<String, Object>> rows = jdbc.sql("select * from device_ops_parse_task " +
                        "where state in ('QUEUED','WAITING') order by created_at limit :limit")
                .param("limit", limit).query().listOfRows();
        int changed = 0;
        for (Map<String, Object> row : rows) {
            boolean available = countAvailable(JdbcParseTaskRepository.coordinate(row), now) > 0;
            if ("QUEUED".equals(row.get("STATE")) && !available) {
                changed += jdbc.sql("update device_ops_parse_task set state='WAITING',wait_reason=:reason," +
                                "updated_at=:now where task_id=:task and state='QUEUED'")
                        .param("reason", ParseWaitReason.NO_CAPABLE_WORKER.name()).param("now", now)
                        .param("task", row.get("TASK_ID")).update();
            } else if ("WAITING".equals(row.get("STATE"))
                    && ParseWaitReason.NO_CAPABLE_WORKER.name().equals(row.get("WAIT_REASON")) && available) {
                changed += jdbc.sql("update device_ops_parse_task set state='QUEUED',wait_reason=null," +
                                "next_attempt_at=:now,updated_at=:now where task_id=:task and state='WAITING'")
                        .param("now", now).param("task", row.get("TASK_ID")).update();
            }
        }
        return changed;
    }

    private static final class MutableHeartbeat {
        private final String workerId;
        private final Instant lastHeartbeatAt;
        private final Instant expiresAt;
        private final Set<WorkerCapability> capabilities = new LinkedHashSet<>();

        private MutableHeartbeat(String workerId, Instant lastHeartbeatAt, Instant expiresAt) {
            this.workerId = workerId;
            this.lastHeartbeatAt = lastHeartbeatAt;
            this.expiresAt = expiresAt;
        }
    }
}
