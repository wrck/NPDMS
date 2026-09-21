package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.core.port.CommandExecutionPort.OutputStreamType;
import com.dp.deviceops.core.service.ExecutionLeaseLostException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/** JDBC implementation deliberately persists only redacted output and parsed facts. */
public final class JdbcCollectionExecutionPersistencePort implements CollectionExecutionPersistencePort {
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final Clock clock;
    private final JdbcCollectionParserTaskAppender parserTasks;

    public JdbcCollectionExecutionPersistencePort(JdbcClient jdbc, TransactionTemplate transaction, ObjectMapper objectMapper) {
        this(jdbc, transaction, objectMapper, Clock.systemUTC(), null);
    }

    public JdbcCollectionExecutionPersistencePort(
            JdbcClient jdbc,
            TransactionTemplate transaction,
            ObjectMapper objectMapper,
            Clock clock) {
        this(jdbc, transaction, objectMapper, clock, null);
    }

    public JdbcCollectionExecutionPersistencePort(
            JdbcClient jdbc,
            TransactionTemplate transaction,
            ObjectMapper objectMapper,
            Clock clock,
            JdbcCollectionParserTaskAppender parserTasks) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transaction = Objects.requireNonNull(transaction);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.parserTasks = parserTasks;
        if (!(transaction.getTransactionManager() instanceof DataSourceTransactionManager dataSourceManager)) {
            throw new IllegalArgumentException("a DataSourceTransactionManager is required");
        }
        this.dataSource = Objects.requireNonNull(dataSourceManager.getDataSource());
        this.namedJdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override public boolean claim(long targetId, String workerId, Instant startedAt, Instant leaseUntil) {
        return claim(targetId, workerId, startedAt, () -> leaseUntil);
    }

    @Override
    public boolean claimPending(long targetId, String workerId, Instant startedAt, Duration leaseDuration) {
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        return claim(targetId, workerId, startedAt, () -> clock.instant().plus(leaseDuration));
    }

    private boolean claim(
            long targetId,
            String workerId,
            Instant startedAt,
            Supplier<Instant> leaseUntil) {
        Boolean claimed = transaction.execute(status -> {
            Instant acquiredLeaseUntil = leaseUntil.get();
            int changed = jdbc.sql("update device_ops_collection_target set lease_owner=:worker,lease_until=:lease where id=:id and status='QUEUED' and lease_owner is null")
                    .param("id", targetId).param("worker", workerId).param("lease", acquiredLeaseUntil).update();
            if (changed != 1) return false;
            Integer next = jdbc.sql("select coalesce(max(attempt_no),0)+1 from device_ops_collection_attempt where target_id=:id")
                    .param("id", targetId).query(Integer.class).single();
            jdbc.sql("insert into device_ops_collection_attempt(target_id,attempt_no,status,lease_owner,lease_until,started_at) values(:id,:number,'QUEUED',:worker,:lease,:started)")
                    .param("id", targetId).param("number", next).param("worker", workerId).param("lease", acquiredLeaseUntil).param("started", startedAt).update();
            Instant finalLeaseUntil = leaseUntil.get();
            int targetLeaseChanged = jdbc.sql("update device_ops_collection_target set lease_until=:lease where id=:id and lease_owner=:worker")
                    .param("id", targetId).param("worker", workerId).param("lease", finalLeaseUntil).update();
            int attemptLeaseChanged = jdbc.sql("update device_ops_collection_attempt set lease_until=:lease where target_id=:id and lease_owner=:worker and finished_at is null")
                    .param("id", targetId).param("worker", workerId).param("lease", finalLeaseUntil).update();
            if (targetLeaseChanged != 1 || attemptLeaseChanged != 1) {
                throw new ExecutionLeaseLostException(targetId);
            }
            return true;
        });
        return Boolean.TRUE.equals(claimed);
    }

    @Override
    public long appendOutput(long targetId, String workerId, int commandIndex,
                             OutputStreamType streamType, String content,
                             long receivedBytes, int pageCount, boolean truncated, Instant createdAt) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(streamType, "streamType");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(createdAt, "createdAt");
        Long sequence = transaction.execute(transactionStatus -> {
            String taskId = jdbc.sql("""
                            select task_id
                            from device_ops_collection_target
                            where id = :id and lease_owner = :worker
                            for update
                            """)
                    .param("id", targetId)
                    .param("worker", workerId)
                    .query(String.class)
                    .optional()
                    .orElseThrow(() -> new ExecutionLeaseLostException(targetId));
            int collectionChanged = jdbc.sql("""
                            update device_ops_collection
                            set output_sequence = output_sequence + 1
                            where task_id = :taskId
                            """)
                    .param("taskId", taskId)
                    .update();
            if (collectionChanged != 1) {
                throw new ExecutionLeaseLostException(targetId);
            }
            Long nextSequence = jdbc.sql("""
                            select output_sequence
                            from device_ops_collection
                            where task_id = :taskId
                            """)
                    .param("taskId", taskId)
                    .query(Long.class)
                    .single();
            jdbc.sql("""
                            insert into device_ops_collection_output_event(
                                task_id, target_id, sequence_no, command_index, stream_type, content,
                                received_bytes, page_count, output_truncated, created_at)
                            values(
                                :taskId, :targetId, :sequence, :commandIndex, :streamType, :content,
                                :receivedBytes, :pageCount, :truncated, :createdAt)
                            """)
                    .param("taskId", taskId)
                    .param("targetId", targetId)
                    .param("sequence", nextSequence)
                    .param("commandIndex", commandIndex)
                    .param("streamType", streamType.name())
                    .param("content", content)
                    .param("receivedBytes", receivedBytes)
                    .param("pageCount", pageCount)
                    .param("truncated", truncated)
                    .param("createdAt", createdAt)
                    .update();
            int targetChanged = jdbc.sql("""
                            update device_ops_collection_target
                            set output_truncated = output_truncated or :truncated
                            where id = :targetId and lease_owner = :worker
                            """)
                    .param("targetId", targetId)
                    .param("worker", workerId)
                    .param("truncated", truncated)
                    .update();
            if (targetChanged != 1) {
                throw new ExecutionLeaseLostException(targetId);
            }
            return nextSequence;
        });
        return Objects.requireNonNull(sequence, "output transaction did not return a sequence");
    }

    @Override
    public void initializeCommandBlocks(long targetId, String workerId, List<CommandOutputBlock> commandBlocks) {
        transaction.executeWithoutResult(status -> {
            requireLease(targetId, workerId);
            for (CommandOutputBlock block : commandBlocks) {
                jdbc.sql("""
                                insert into device_ops_collection_command_output(
                                    target_id, command_index, command_text, status,
                                    standard_output, standard_error, received_bytes, page_count,
                                    output_truncated, exit_code, outcome_message, parsed_facts_json,
                                    parse_warnings_json, started_at, completed_at, legacy_record)
                                values(:targetId,:commandIndex,:commandText,:status,'','',0,0,false,null,null,
                                    '{}','[]',null,null,false)
                                """)
                        .param("targetId", targetId)
                        .param("commandIndex", block.commandIndex())
                        .param("commandText", block.commandText())
                        .param("status", block.status().name())
                        .update();
            }
        });
    }

    @Override
    public void updateCommandBlocks(long targetId, String workerId, List<CommandOutputBlock> commandBlocks) {
        transaction.executeWithoutResult(status -> {
            requireLease(targetId, workerId);
            for (CommandOutputBlock block : commandBlocks) {
                int changed = jdbc.sql("""
                                update device_ops_collection_command_output
                                set status=:status, standard_output=:stdout, standard_error=:stderr,
                                    received_bytes=:receivedBytes, page_count=:pageCount,
                                    output_truncated=:truncated, exit_code=:exitCode,
                                    outcome_message=:outcome, parsed_facts_json=:facts,
                                    parse_warnings_json=:warnings, started_at=:startedAt,
                                    completed_at=:completedAt
                                where target_id=:targetId and command_index=:commandIndex
                                """)
                        .param("targetId", targetId)
                        .param("commandIndex", block.commandIndex())
                        .param("status", block.status().name())
                        .param("stdout", block.stdout())
                        .param("stderr", block.stderr())
                        .param("receivedBytes", block.receivedBytes())
                        .param("pageCount", block.pageCount())
                        .param("truncated", block.truncated())
                        .param("exitCode", block.exitCode())
                        .param("outcome", block.outcome())
                        .param("facts", serializeObject(block.parsedFacts()))
                        .param("warnings", serializeObject(block.parseWarnings()))
                        .param("startedAt", block.startedAt())
                        .param("completedAt", block.completedAt())
                        .update();
                if (changed != 1) {
                    throw new IllegalStateException("command output block is missing");
                }
            }
        });
    }

    private void requireLease(long targetId, String workerId) {
        boolean owned = jdbc.sql("select count(*) from device_ops_collection_target where id=:id and lease_owner=:worker")
                .param("id", targetId).param("worker", workerId).query(Integer.class).single() == 1;
        if (!owned) {
            throw new ExecutionLeaseLostException(targetId);
        }
    }

    @Override
    public void updateOutputSnapshot(
            long targetId,
            String workerId,
            String stdout,
            String stderr,
            boolean truncated) {
        int changed = jdbc.sql("""
                        update device_ops_collection_target
                        set standard_output = :stdout,
                            standard_error = :stderr,
                            output_truncated = output_truncated or :truncated
                        where id = :targetId and lease_owner = :worker
                        """)
                .param("targetId", targetId)
                .param("worker", workerId)
                .param("stdout", Objects.requireNonNull(stdout, "stdout"))
                .param("stderr", Objects.requireNonNull(stderr, "stderr"))
                .param("truncated", truncated)
                .update();
        if (changed != 1) {
            throw new ExecutionLeaseLostException(targetId);
        }
    }

    @Override public void updateTarget(long targetId, String workerId, CollectionStatus status, String stdout, String stderr, Integer exitCode, String outcome, boolean truncated, Map<String, String> parsedFacts) {
        String facts = serialize(parsedFacts);
        transaction.executeWithoutResult(transactionStatus -> {
            int targetChanged = jdbc.sql("update device_ops_collection_target set status=:status,standard_output=:stdout,standard_error=:stderr,exit_code=:exitCode,outcome_message=:outcome,output_truncated=:truncated,parsed_facts_json=:facts,lease_owner=case when :terminal then null else lease_owner end,lease_until=case when :terminal then null else lease_until end where id=:id and lease_owner=:worker")
                    .param("id", targetId).param("worker", workerId).param("status", status.name()).param("stdout", stdout).param("stderr", stderr).param("exitCode", exitCode).param("outcome", outcome).param("truncated", truncated).param("facts", facts).param("terminal", terminal(status)).update();
            if (targetChanged != 1) throw new ExecutionLeaseLostException(targetId);
            int attemptChanged = jdbc.sql("update device_ops_collection_attempt set status=:status,finished_at=case when :terminal then current_timestamp else finished_at end,failure_reason=case when :terminal then :outcome else failure_reason end where target_id=:id and lease_owner=:worker and finished_at is null")
                    .param("id", targetId).param("worker", workerId).param("status", status.name()).param("terminal", terminal(status)).param("outcome", outcome).update();
            if (attemptChanged != 1) throw new ExecutionLeaseLostException(targetId);
            if (terminal(status)) {
                finalizeIncompleteBlocks(targetId, status, outcome);
                appendParserTask(targetId, status);
                appendCallbackEvent(targetId, status, stdout, stderr, exitCode, parsedFacts);
            }
        });
    }

    private void finalizeIncompleteBlocks(long targetId, CollectionStatus targetStatus, String outcome) {
        String runningStatus = targetStatus == CollectionStatus.TIMED_OUT ? "TIMED_OUT" : "FAILED";
        jdbc.sql("""
                        update device_ops_collection_command_output
                        set status=case when status='RUNNING' then :runningStatus else 'CANCELLED' end,
                            outcome_message=coalesce(outcome_message,:outcome),
                            completed_at=coalesce(completed_at,current_timestamp)
                        where target_id=:targetId and status in ('PENDING','RUNNING')
                        """)
                .param("targetId", targetId)
                .param("runningStatus", runningStatus)
                .param("outcome", outcome)
                .update();
    }

    @Override public void renewLease(long targetId, String workerId, Instant leaseUntil) {
        transaction.executeWithoutResult(transactionStatus -> {
            int targetChanged = jdbc.sql("update device_ops_collection_target set lease_until=:lease where id=:id and lease_owner=:worker and status in ('QUEUED','CONNECTING','EXECUTING','PARSING')")
                    .param("id", targetId).param("worker", workerId).param("lease", leaseUntil).update();
            if (targetChanged != 1) throw new ExecutionLeaseLostException(targetId);
            int attemptChanged = jdbc.sql("update device_ops_collection_attempt set lease_until=:lease where target_id=:id and lease_owner=:worker and finished_at is null")
                    .param("id", targetId).param("worker", workerId).param("lease", leaseUntil).update();
            if (attemptChanged != 1) throw new ExecutionLeaseLostException(targetId);
        });
    }

    @Override
    public void renewPendingLease(long targetId, String workerId, Instant leaseUntil) {
        List<Long> leaseLost = renewPendingLeases(
                List.of(new ClaimedTarget(targetId, workerId)), leaseUntil);
        if (!leaseLost.isEmpty()) throw new ExecutionLeaseLostException(targetId);
    }

    @Override
    public List<Long> renewPendingLeases(List<ClaimedTarget> targets, Instant leaseUntil) {
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        return renewPendingLeases(targets, () -> leaseUntil);
    }

    @Override
    public List<Long> renewPendingLeases(List<ClaimedTarget> targets, Duration leaseDuration) {
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        return renewPendingLeases(targets, () -> clock.instant().plus(leaseDuration));
    }

    private List<Long> renewPendingLeases(
            List<ClaimedTarget> targets,
            Supplier<Instant> leaseUntil) {
        Objects.requireNonNull(targets, "targets");
        if (targets.isEmpty()) return List.of();
        List<ClaimedTarget> remaining = new ArrayList<>(targets);
        List<Long> leaseLost = new ArrayList<>();
        while (!remaining.isEmpty()) {
            HeartbeatBatch batch = renewHeartbeatBatch(remaining, leaseUntil);
            if (batch.leaseLost().isEmpty()) break;
            leaseLost.addAll(batch.leaseLost());
            remaining.removeIf(target -> batch.leaseLost().contains(target.targetId()));
        }
        return List.copyOf(leaseLost);
    }

    private HeartbeatBatch renewHeartbeatBatch(
            List<ClaimedTarget> targets,
            Supplier<Instant> leaseUntil) {
        return transaction.execute(status -> {
            Map<Long, LeaseOwnership> ownership = new HashMap<>();
            namedJdbc.query(
                    "select t.id as target_id, t.lease_owner as target_owner, "
                            + "a.lease_owner as attempt_owner "
                            + "from device_ops_collection_target t "
                            + "left join device_ops_collection_attempt a "
                            + "on a.target_id=t.id and a.finished_at is null "
                            + "where t.id in (:ids) "
                            + "and t.status in ('QUEUED','CONNECTING','EXECUTING','PARSING') "
                            + "for update",
                    new MapSqlParameterSource("ids", targets.stream()
                            .map(ClaimedTarget::targetId).toList()),
                    (org.springframework.jdbc.core.RowCallbackHandler) resultSet -> ownership.put(
                            resultSet.getLong("target_id"),
                            new LeaseOwnership(
                                    resultSet.getString("target_owner"),
                                    resultSet.getString("attempt_owner"))));
            List<Long> lost = new ArrayList<>();
            for (ClaimedTarget target : targets) {
                LeaseOwnership current = ownership.get(target.targetId());
                if (current == null
                        || !target.workerId().equals(current.targetOwner())
                        || !target.workerId().equals(current.attemptOwner())) {
                    lost.add(target.targetId());
                }
            }
            if (!lost.isEmpty()) {
                status.setRollbackOnly();
                return new HeartbeatBatch(List.copyOf(lost));
            }
            Instant finalLeaseUntil = leaseUntil.get();
            MapSqlParameterSource[] leaseParameters = targets.stream()
                    .map(target -> new MapSqlParameterSource()
                            .addValue("id", target.targetId())
                            .addValue("worker", target.workerId())
                            .addValue("lease", finalLeaseUntil))
                    .toArray(MapSqlParameterSource[]::new);
            namedJdbc.batchUpdate(
                    "update device_ops_collection_target "
                            + "set lease_until=case when lease_until is null or lease_until<:lease "
                            + "then :lease else lease_until end "
                            + "where id=:id and lease_owner=:worker",
                    leaseParameters);
            namedJdbc.batchUpdate(
                    "update device_ops_collection_attempt "
                            + "set lease_until=case when lease_until is null or lease_until<:lease "
                            + "then :lease else lease_until end "
                            + "where target_id=:id and lease_owner=:worker and finished_at is null",
                    leaseParameters);
            return new HeartbeatBatch(List.copyOf(lost));
        });
    }

    private record HeartbeatBatch(List<Long> leaseLost) {
    }

    private record LeaseOwnership(String targetOwner, String attemptOwner) {
    }

    @Override public int failUnclaimedTargets(java.util.List<Long> targetIds, String reason) {
        if (targetIds.isEmpty()) return 0;
        return transaction.execute(status -> {
            List<Long> eligible = jdbc.sql("select id from device_ops_collection_target "
                            + "where id in (:ids) and status='QUEUED' and lease_owner is null for update")
                    .param("ids", targetIds).query(Long.class).list();
            int changed = 0;
            for (Long targetId : eligible) {
                int updated = jdbc.sql("update device_ops_collection_target set status='FAILED',outcome_message=:reason "
                                + "where id=:id and status='QUEUED' and lease_owner is null")
                        .param("id", targetId).param("reason", reason).update();
                if (updated == 1) {
                    finalizeIncompleteBlocks(targetId, CollectionStatus.FAILED, reason);
                    appendParserTask(targetId, CollectionStatus.FAILED);
                    changed++;
                }
            }
            return changed;
        });
    }

    @Override public int failRecoveredTargets(Instant now, String reason) {
        return failRecoverableTargets(now, now, reason, Integer.MAX_VALUE);
    }

    @Override public int failExpiredTargets(Instant now, String reason) {
        return failRecoverableTargets(now, Instant.EPOCH, reason, Integer.MAX_VALUE);
    }

    @Override public int failRecoverableTargets(
            Instant now,
            Instant unclaimedBefore,
            String reason,
            int batchSize) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(unclaimedBefore, "unclaimedBefore");
        Objects.requireNonNull(reason, "reason");
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
        return transaction.execute(status -> {
            java.util.List<RecoverableTarget> targets = jdbc.sql("""
                            select id, standard_output, standard_error, exit_code,
                                   output_truncated, parsed_facts_json
                            from device_ops_collection_target
                            where (status='QUEUED' and lease_owner is null and queued_at < :unclaimedBefore)
                               or (status in ('QUEUED','CONNECTING','EXECUTING','PARSING')
                                    and lease_owner is not null and lease_until < :now)
                            order by id
                            limit :batchSize
                            for update
                            """)
                    .param("unclaimedBefore", unclaimedBefore)
                    .param("now", now)
                    .param("batchSize", batchSize)
                    .query((resultSet, rowNumber) -> new RecoverableTarget(
                            resultSet.getLong("id"),
                            resultSet.getString("standard_output"),
                            resultSet.getString("standard_error"),
                            resultSet.getObject("exit_code", Integer.class),
                            resultSet.getBoolean("output_truncated"),
                            resultSet.getString("parsed_facts_json")))
                    .list();
            int count = 0;
            for (RecoverableTarget target : targets) {
                TerminalEvidence evidence = terminalEvidence(
                        target.id(), target.stdout(), target.stderr(), target.exitCode(),
                        target.truncated(), target.factsJson());
                int targetChanged = jdbc.sql("""
                                update device_ops_collection_target
                                set status='FAILED', outcome_message=:reason,
                                    standard_output=:stdout, standard_error=:stderr,
                                    output_truncated=:truncated, lease_owner=null, lease_until=null
                                where id=:id
                                  and ((status='QUEUED' and lease_owner is null and queued_at < :unclaimedBefore)
                                   or (status in ('QUEUED','CONNECTING','EXECUTING','PARSING')
                                        and lease_owner is not null and lease_until < :now))
                                """)
                        .param("id", target.id())
                        .param("reason", reason)
                        .param("stdout", evidence.stdout())
                        .param("stderr", evidence.stderr())
                        .param("truncated", evidence.truncated())
                        .param("now", now)
                        .param("unclaimedBefore", unclaimedBefore)
                        .update();
                if (targetChanged != 1) continue;
                finalizeIncompleteBlocks(target.id(), CollectionStatus.FAILED, reason);
                appendParserTask(target.id(), CollectionStatus.FAILED);
                jdbc.sql("update device_ops_collection_attempt set status='FAILED',finished_at=:now,failure_reason=:reason where finished_at is null and target_id=:id")
                        .param("id", target.id()).param("now", now).param("reason", reason).update();
                appendCallbackEvent(
                        target.id(), CollectionStatus.FAILED,
                        evidence.stdout(), evidence.stderr(), evidence.exitCode(), evidence.facts());
                count++;
            }
            return count;
        });
    }

    @Override public void cancelClaimedTarget(long targetId, String workerId, String reason) {
        transaction.executeWithoutResult(transactionStatus -> {
            CancellationEvidence persisted = jdbc.sql("select standard_output,standard_error,exit_code,output_truncated,parsed_facts_json from device_ops_collection_target where id=:id and lease_owner=:worker for update")
                    .param("id", targetId).param("worker", workerId)
                    .query((resultSet, rowNumber) -> new CancellationEvidence(
                            resultSet.getString("standard_output"),
                            resultSet.getString("standard_error"),
                            resultSet.getObject("exit_code", Integer.class),
                            resultSet.getBoolean("output_truncated"),
                            resultSet.getString("parsed_facts_json")))
                    .optional()
                    .orElseThrow(() -> new ExecutionLeaseLostException(targetId));
            TerminalEvidence evidence = terminalEvidence(
                    targetId, persisted.stdout(), persisted.stderr(), persisted.exitCode(),
                    persisted.truncated(), persisted.factsJson());
            int targetChanged = jdbc.sql("update device_ops_collection_target set status='CANCELLED',outcome_message=:reason,standard_output=:stdout,standard_error=:stderr,output_truncated=:truncated,lease_owner=null,lease_until=null where id=:id and lease_owner=:worker and status in ('QUEUED','CONNECTING','EXECUTING','PARSING')")
                    .param("id", targetId).param("worker", workerId).param("reason", reason)
                    .param("stdout", evidence.stdout()).param("stderr", evidence.stderr())
                    .param("truncated", evidence.truncated()).update();
            if (targetChanged != 1) throw new ExecutionLeaseLostException(targetId);
            finalizeIncompleteBlocks(targetId, CollectionStatus.CANCELLED, reason);
            int attemptChanged = jdbc.sql("update device_ops_collection_attempt set status='CANCELLED',finished_at=current_timestamp,failure_reason=:reason where target_id=:id and lease_owner=:worker and finished_at is null")
                    .param("id", targetId).param("worker", workerId).param("reason", reason).update();
            if (attemptChanged != 1) throw new ExecutionLeaseLostException(targetId);
            appendCallbackEvent(
                    targetId,
                    CollectionStatus.CANCELLED,
                    evidence.stdout(),
                    evidence.stderr(),
                    evidence.exitCode(),
                    evidence.facts());
        });
    }

    private TerminalEvidence terminalEvidence(
            long targetId,
            String snapshotStdout,
            String snapshotStderr,
            Integer exitCode,
            boolean snapshotTruncated,
            String factsJson) {
        StringBuilder eventStdout = new StringBuilder();
        StringBuilder eventStderr = new StringBuilder();
        boolean[] eventTruncated = {false};
        jdbc.sql("select stream_type,content,output_truncated from device_ops_collection_output_event where target_id=:id order by sequence_no")
                .param("id", targetId)
                .query((resultSet, rowNumber) -> {
                    String content = Objects.toString(resultSet.getString("content"), "");
                    if (OutputStreamType.STDOUT.name().equals(resultSet.getString("stream_type"))) {
                        eventStdout.append(content);
                    } else {
                        eventStderr.append(content);
                    }
                    eventTruncated[0] |= resultSet.getBoolean("output_truncated");
                    return rowNumber;
                })
                .list();
        return new TerminalEvidence(
                mostComplete(snapshotStdout, eventStdout.toString()),
                mostComplete(snapshotStderr, eventStderr.toString()),
                exitCode,
                snapshotTruncated || eventTruncated[0],
                deserializeFacts(factsJson));
    }

    private static String mostComplete(String snapshot, String events) {
        String safeSnapshot = Objects.toString(snapshot, "");
        if (events.startsWith(safeSnapshot)) return events;
        if (safeSnapshot.startsWith(events)) return safeSnapshot;
        return events.length() > safeSnapshot.length() ? events : safeSnapshot;
    }

    private Map<String, String> deserializeFacts(Object value) {
        if (value == null) return Map.of();
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("parsed facts cannot be decoded", exception);
        }
    }
    private record RecoverableTarget(
            long id, String stdout, String stderr, Integer exitCode, boolean truncated, String factsJson) { }
    private record CancellationEvidence(
            String stdout, String stderr, Integer exitCode, boolean truncated, String factsJson) { }
    private record TerminalEvidence(
            String stdout, String stderr, Integer exitCode, boolean truncated, Map<String, String> facts) { }
    private String serialize(Map<String, String> facts) { return serializeObject(facts == null ? Map.of() : facts); }
    private String serializeObject(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new IllegalArgumentException("collection evidence cannot be serialized", exception); } }
    private void appendCallbackEvent(long targetId, CollectionStatus status, String stdout, String stderr, Integer exitCode, Map<String,String> facts) {
        var row=jdbc.sql("select c.task_id,c.namespace,c.external_request_id,c.project_key,c.callback_uri,c.script_key,c.script_version,c.script_sha256,t.device_key,t.outcome_message,t.output_truncated from device_ops_collection_target t join device_ops_collection c on c.task_id=t.task_id where t.id=:id").param("id",targetId).query().singleRow();
        String destination=(String)row.get("CALLBACK_URI");
        if(destination==null||destination.isBlank()||row.get("PROJECT_KEY")==null||row.get("DEVICE_KEY")==null)return;
        Integer number=jdbc.sql("select max(attempt_no) from device_ops_collection_attempt where target_id=:id").param("id",targetId).query(Integer.class).single();
        String eventId=sha(targetId+":"+number+":"+status.name());
        List<Map<String,Object>> commandBlocks = jdbc.sql("select command_index,command_text,status,standard_output,standard_error,received_bytes,page_count,output_truncated,exit_code,outcome_message,parsed_facts_json,parse_warnings_json,started_at,completed_at,legacy_record from device_ops_collection_command_output where target_id=:id order by command_index")
                .param("id", targetId).query((resultSet, rowNumber) -> {
                    Map<String, Object> block = new java.util.LinkedHashMap<>();
                    block.put("commandIndex", resultSet.getInt("command_index"));
                    block.put("commandText", resultSet.getString("command_text"));
                    block.put("status", resultSet.getString("status"));
                    block.put("stdout", resultSet.getString("standard_output"));
                    block.put("stderr", resultSet.getString("standard_error"));
                    block.put("receivedBytes", resultSet.getLong("received_bytes"));
                    block.put("pageCount", resultSet.getInt("page_count"));
                    block.put("truncated", resultSet.getBoolean("output_truncated"));
                    block.put("exitCode", resultSet.getObject("exit_code", Integer.class));
                    block.put("outcome", resultSet.getString("outcome_message"));
                    block.put("parsedFacts", deserializeFacts(resultSet.getString("parsed_facts_json")));
                    block.put("parseWarnings", deserializeStrings(resultSet.getString("parse_warnings_json")));
                    block.put("startedAt", resultSet.getTimestamp("started_at"));
                    block.put("completedAt", resultSet.getTimestamp("completed_at"));
                    block.put("legacy", resultSet.getBoolean("legacy_record"));
                    return block;
                }).list();
        Map<String,Object> payload=new java.util.LinkedHashMap<>(); payload.put("eventId",eventId);payload.put("namespace",row.get("NAMESPACE"));payload.put("outcome",row.get("OUTCOME_MESSAGE"));payload.put("truncated",row.get("OUTPUT_TRUNCATED"));payload.put("collectionId",row.get("TASK_ID"));payload.put("externalRequestId",row.get("EXTERNAL_REQUEST_ID"));payload.put("projectKey",row.get("PROJECT_KEY"));payload.put("deviceKey",row.get("DEVICE_KEY"));payload.put("status",status.name());payload.put("exitCode",exitCode);payload.put("stdout",stdout);payload.put("stderr",stderr);payload.put("parsedFacts",facts==null?Map.of():facts);payload.put("commandBlocks",commandBlocks);payload.put("script",Map.of("key",row.get("SCRIPT_KEY"),"version",row.get("SCRIPT_VERSION"),"sha256",row.get("SCRIPT_SHA256")));
        try { jdbc.sql("insert into device_ops_outbox(event_id,aggregate_id,event_type,payload,attempt_count,next_attempt_at,destination) values(:id,:aggregate,'COLLECTION_TARGET_TERMINAL',:payload,0,current_timestamp,:destination)").param("id",eventId).param("aggregate",row.get("TASK_ID")).param("payload",objectMapper.writeValueAsString(payload)).param("destination",destination).update(); } catch (JsonProcessingException e) { throw new IllegalStateException("callback payload cannot be encoded",e); }
    }
    private static String sha(String input){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private List<String> deserializeStrings(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return objectMapper.readValue(value, new TypeReference<>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("parse warnings cannot be decoded", exception); }
    }
    private static boolean terminal(CollectionStatus status) { return status == CollectionStatus.SUCCEEDED || status == CollectionStatus.PARTIAL_SUCCESS || status == CollectionStatus.FAILED || status == CollectionStatus.TIMED_OUT || status == CollectionStatus.CANCELLED; }
    private void appendParserTask(long targetId, CollectionStatus status) {
        if (parserTasks != null) parserTasks.append(targetId, status);
    }
}
