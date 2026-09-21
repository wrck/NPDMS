package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionContextSnapshot;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CollectionTask;
import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.CollectionSemanticParsing;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.CollectionRepository;
import com.dp.deviceops.core.port.CollectionTargetReferencePort;
import com.dp.deviceops.core.port.ScriptArtifactRepository;
import com.dp.deviceops.core.service.CollectionIdempotencyConflictException;
import org.springframework.transaction.TransactionDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** JDBC persistence for immutable submitted collection evidence. */
public final class CollectionJdbcRepository implements CollectionRepository, CollectionTargetReferencePort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final ScriptArtifactRepository scripts;

    public CollectionJdbcRepository(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        this(jdbc, transactionManager, new JdbcScriptArtifactRepository(jdbc));
    }

    public CollectionJdbcRepository(JdbcClient jdbc, PlatformTransactionManager transactionManager,
                                    ScriptArtifactRepository scripts) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        // A duplicate must roll back only this submission, not poison an enclosing transaction.
        // NESTED keeps successful writes subject to the caller's rollback via a JDBC savepoint.
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        this.scripts = Objects.requireNonNull(scripts, "scripts must not be null");
    }

    @Override public Optional<CollectionTask> findByIdempotencyKey(String namespace, String key) {
        return jdbc.sql("SELECT task_id,namespace,idempotency_key FROM device_ops_collection WHERE namespace=:namespace AND idempotency_key=:key")
                .param("namespace", namespace).param("key", key)
                .query((rs, row) -> namespace.equals(rs.getString("namespace"))
                        && key.equals(rs.getString("idempotency_key")) ? rs.getString("task_id") : null)
                .optional().map(this::readTask);
    }

    @Override public SaveResult saveOrGetExisting(CollectionTask candidate) {
        try {
            return transactions.execute(status -> {
                insertSubmissionGuard(candidate.namespace(), candidate.idempotencyKey(), false);
                insertCollection(candidate);
                candidate.semanticParsing().ifPresent(parsing -> insertSemanticParsing(candidate.taskId(), parsing));
                scripts.save(candidate.namespace(), candidate.script());
                candidate.targets().forEach(target -> insertTarget(candidate.taskId(), target));
                return new SaveResult(candidate, true);
            });
        } catch (DuplicateKeyException exception) {
            // A winner inserted by the enclosing transaction is visible only on this connection.
            if (findByIdempotencyKey(candidate.namespace(), candidate.idempotencyKey()).isPresent()) {
                return replayWinner(candidate, exception);
            }
            // A losing REPEATABLE_READ snapshot may not see the now-committed winner.
            TransactionTemplate currentRead = new TransactionTemplate(
                    Objects.requireNonNull(transactions.getTransactionManager()));
            currentRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            currentRead.setReadOnly(true);
            return currentRead.execute(status -> replayWinner(candidate, exception));
        }
    }

    private SaveResult replayWinner(CollectionTask candidate, DuplicateKeyException duplicate) {
        if (isCancelledBeforeSubmission(candidate.namespace(), candidate.idempotencyKey())) {
            throw new CollectionIdempotencyConflictException();
        }
        Optional<CollectionTask> existing = findByIdempotencyKey(candidate.namespace(), candidate.idempotencyKey());
        if (existing.isPresent()) {
            CollectionTask winner = existing.orElseThrow();
            if (candidate.submissionFingerprint() != null
                    && (!candidate.submissionFingerprint().equals(winner.submissionFingerprint())
                    || !candidate.projectKey().equals(winner.projectKey()))) {
                throw new CollectionIdempotencyConflictException();
            }
            return new SaveResult(winner, false);
        }
        // SQL equality deliberately detects CI unique-key collisions, but never returns their IDs.
        boolean keyCollision = jdbc.sql("SELECT COUNT(*) FROM device_ops_collection WHERE namespace=:namespace "
                        + "AND (idempotency_key=:key OR external_request_id=:external)")
                .param("namespace", candidate.namespace()).param("key", candidate.idempotencyKey())
                .param("external", candidate.externalRequestId()).query(Long.class).single() > 0;
        if (keyCollision) {
            throw new CollectionIdempotencyConflictException();
        }
        throw duplicate;
    }

    private void insertSubmissionGuard(String namespace, String key, boolean cancelled) {
        jdbc.sql("INSERT INTO device_ops_collection_submission_guard (namespace,idempotency_key,cancelled) VALUES (:namespace,:key,:cancelled)")
                .param("namespace", namespace).param("key", key).param("cancelled", cancelled).update();
    }

    @Override public boolean cancelBeforeSubmission(String namespace, String key) {
        // The unique insert competes with saveOrGetExisting in the same database. No check-then-insert race.
        try {
            transactions.executeWithoutResult(status -> insertSubmissionGuard(namespace, key, true));
            return true;
        } catch (DuplicateKeyException existing) {
            TransactionTemplate currentRead = new TransactionTemplate(transactions.getTransactionManager());
            currentRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return Boolean.TRUE.equals(currentRead.execute(status -> isCancelledBeforeSubmission(namespace, key)));
        }
    }

    @Override public boolean isCancelledBeforeSubmission(String namespace, String key) {
        return jdbc.sql("SELECT namespace,idempotency_key,cancelled FROM device_ops_collection_submission_guard WHERE namespace=:namespace AND idempotency_key=:key")
                .param("namespace", namespace).param("key", key).query((rs, row) -> {
                    if (!namespace.equals(rs.getString("namespace")) || !key.equals(rs.getString("idempotency_key"))) {
                        throw new CollectionIdempotencyConflictException();
                    }
                    return rs.getBoolean("cancelled");
                }).optional().orElse(false);
    }

    public void appendAttempt(long targetId, Attempt attempt) {
        try {
            jdbc.sql("INSERT INTO device_ops_collection_attempt (target_id,attempt_no,status,lease_owner,lease_until,started_at,finished_at,failure_reason) VALUES (:targetId,:number,:status,:owner,:leaseUntil,:startedAt,:finishedAt,:failureReason)")
                    .param("targetId", targetId).param("number", attempt.number()).param("status", attempt.status()).param("owner", attempt.leaseOwner()).param("leaseUntil", attempt.leaseUntil()).param("startedAt", attempt.startedAt()).param("finishedAt", attempt.finishedAt()).param("failureReason", attempt.failureReason()).update();
        } catch (DuplicateKeyException duplicate) { throw new IllegalStateException("attempt number already exists", duplicate); }
    }

    public List<Attempt> findAttempts(long targetId) {
        return jdbc.sql("SELECT attempt_no,status,lease_owner,lease_until,started_at,finished_at,failure_reason FROM device_ops_collection_attempt WHERE target_id=:targetId ORDER BY attempt_no")
                .param("targetId", targetId).query((rs, row) -> new Attempt(rs.getInt("attempt_no"), rs.getString("status"), rs.getString("lease_owner"), instant(rs, "lease_until"), instant(rs, "started_at"), instant(rs, "finished_at"), rs.getString("failure_reason"))).list();
    }
    @Override public List<Long> findTargetIds(String collectionId) {
        return jdbc.sql("SELECT id FROM device_ops_collection_target WHERE task_id=:id ORDER BY id").param("id", collectionId).query(Long.class).list();
    }

    private void insertCollection(CollectionTask task) {
        ScriptArtifact script = task.script();
        jdbc.sql("INSERT INTO device_ops_collection (task_id,namespace,project_key,external_request_id,idempotency_key,activity_type,callback_uri,script_source,script_key,script_version,script_content,script_sha256,script_policy,parser_type,parser_config,submission_fingerprint,submission_snapshot_json) VALUES (:id,:namespace,:projectKey,:externalRequestId,:idempotencyKey,:activityType,:callbackUri,:source,:scriptKey,:version,:content,:sha256,:policy,:parserType,:parserConfig,:submissionFingerprint,:submissionSnapshot)")
                .param("submissionSnapshot", task.submissionSnapshot())
                .param("submissionFingerprint", task.submissionFingerprint())
                .param("id", task.taskId()).param("namespace", task.namespace()).param("projectKey", task.projectKey().orElse(null)).param("externalRequestId", task.externalRequestId()).param("idempotencyKey", task.idempotencyKey()).param("activityType", task.activityType()).param("callbackUri", task.callbackUri() == null ? null : task.callbackUri().toString()).param("source", script.source().name()).param("scriptKey", script.key()).param("version", script.version()).param("content", script.content()).param("sha256", script.sha256()).param("policy", script.persistencePolicy().name()).param("parserType", script.parserType()).param("parserConfig", script.parserConfig()).update();
    }

    private void insertTarget(String taskId, CollectionTarget target) {
        CollectionContextSnapshot context = target.contextSnapshot();
        CollectionTarget.EndpointSnapshot endpoint = target.endpointSnapshot();
        CollectionContextSnapshot.ProjectSnapshot project = context.project().orElse(null);
        CollectionContextSnapshot.DeviceSnapshot device = context.device().orElse(null);
        jdbc.sql("INSERT INTO device_ops_collection_target (task_id,project_name,project_code,device_key,device_name,vendor,model,extensions_json,protocol,telnet_prompts_json,host,port,username,host_key_fingerprint,status,standard_output,standard_error,exit_code,outcome_message,output_truncated) VALUES (:taskId,:projectName,:projectCode,:deviceKey,:deviceName,:vendor,:model,:extensions,:protocol,:telnetPrompts,:host,:port,:username,:fingerprint,:status,:stdout,:stderr,:exitCode,:outcome,:truncated)")
                .param("taskId", taskId).param("projectName", project == null ? null : project.projectName()).param("projectCode", project == null ? null : project.projectCode()).param("deviceKey", device == null ? null : device.deviceKey()).param("deviceName", device == null ? null : device.deviceName()).param("vendor", device == null ? null : device.vendor()).param("model", device == null ? null : device.model()).param("extensions", json(context.extensions())).param("protocol", endpoint.protocol().name()).param("telnetPrompts", json(endpoint.telnetPrompts())).param("host", endpoint.host()).param("port", endpoint.port()).param("username", endpoint.username()).param("fingerprint", endpoint.hostKeyFingerprint()).param("status", target.status().name()).param("stdout", target.standardOutput()).param("stderr", target.standardError()).param("exitCode", target.exitCode().orElse(null)).param("outcome", target.outcomeMessage().orElse(null)).param("truncated", target.outputTruncated()).update();
    }

    private void insertSemanticParsing(String taskId, CollectionSemanticParsing parsing) {
        jdbc.sql("INSERT INTO device_ops_collection_parser_request "
                        + "(task_id,log_type,release_id,input_format,result_consumer_id,result_destination) "
                        + "VALUES (:task,:type,:release,:format,:consumer,:destination)")
                .param("task", taskId).param("type", parsing.logType()).param("release", parsing.releaseId())
                .param("format", parsing.inputFormat()).param("consumer", parsing.resultConsumerId())
                .param("destination", parsing.resultDestination() == null ? null : parsing.resultDestination().toString())
                .update();
    }

    private CollectionTask readTask(String taskId) {
        var row = jdbc.sql("SELECT * FROM device_ops_collection WHERE task_id=:id").param("id", taskId).query().singleRow();
        ScriptArtifact script = artifact((String) row.get("SCRIPT_SOURCE"), (String) row.get("SCRIPT_KEY"), (String) row.get("SCRIPT_VERSION"), (String) row.get("SCRIPT_CONTENT"), (String) row.get("SCRIPT_SHA256"), (String) row.get("SCRIPT_POLICY"), (String) row.get("PARSER_TYPE"), (String) row.get("PARSER_CONFIG"));
        String namespace = (String) row.get("NAMESPACE");
        String projectKey = (String) row.get("PROJECT_KEY");
        List<CollectionTarget> targets = jdbc.sql("SELECT * FROM device_ops_collection_target WHERE task_id=:id ORDER BY id")
                .param("id", taskId)
                .query((rs, index) -> CollectionTarget.restore(
                        CollectionContextSnapshot.ofOptional(
                                projectKey == null ? null : new CollectionContextSnapshot.ProjectSnapshot(
                                        namespace, projectKey, rs.getString("project_name"), rs.getString("project_code")),
                                rs.getString("device_key") == null ? null : new CollectionContextSnapshot.DeviceSnapshot(
                                        rs.getString("device_key"), rs.getString("device_name"),
                                        rs.getString("vendor"), rs.getString("model")),
                                map(rs.getString("extensions_json"))),
                        ConnectionProtocol.valueOf(rs.getString("protocol")),
                        rs.getString("host"), rs.getInt("port"), rs.getString("username"),
                        rs.getString("host_key_fingerprint"), prompts(rs.getString("telnet_prompts_json")),
                        CollectionStatus.valueOf(rs.getString("status")), rs.getString("standard_output"),
                        rs.getString("standard_error"), rs.getObject("exit_code", Integer.class),
                        rs.getString("outcome_message"), rs.getBoolean("output_truncated")))
                .list();
        CollectionSemanticParsing semanticParsing = jdbc.sql(
                        "SELECT * FROM device_ops_collection_parser_request WHERE task_id=:id")
                .param("id", taskId).query((rs, index) -> new CollectionSemanticParsing(
                        rs.getString("log_type"), rs.getString("release_id"), rs.getString("input_format"),
                        rs.getString("result_consumer_id"), rs.getString("result_destination") == null
                                ? null : URI.create(rs.getString("result_destination")))).optional().orElse(null);
        return CollectionTask.submitted((String) row.get("TASK_ID"), namespace, projectKey,
                (String) row.get("EXTERNAL_REQUEST_ID"), (String) row.get("IDEMPOTENCY_KEY"), targets, script,
                (String) row.get("ACTIVITY_TYPE"),
                row.get("CALLBACK_URI") == null ? null : URI.create((String) row.get("CALLBACK_URI")),
                semanticParsing, (String) row.get("SUBMISSION_FINGERPRINT"))
                .withSubmissionSnapshot(jdbc.sql("SELECT submission_snapshot_json FROM device_ops_collection WHERE task_id=:id")
                        .param("id", taskId).query((rs, index) -> rs.getString(1)).list().getFirst());
    }

    private static ScriptArtifact artifact(String source, String key, String version, String content, String sha, String policy, String parser, String config) {
        return switch (ScriptArtifact.ScriptSource.valueOf(source)) {
            case LOCAL_MANAGED -> ScriptArtifact.local(key, version, content, sha, ScriptArtifact.PersistencePolicy.valueOf(policy), parser, config);
            case EXTERNAL_DELIVERED -> ScriptArtifact.external(key, version, content, sha, ScriptArtifact.PersistencePolicy.valueOf(policy), parser, config);
            case ADHOC_INLINE -> ScriptArtifact.adHoc(key, version, content, sha, parser, config);
        };
    }
    private static String json(Object value) { if (value == null) return null; try { return JSON.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static Map<String, String> map(String value) { try { return JSON.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static CommandExecutionPort.TelnetPrompts prompts(String value) { if (value == null) return null; try { return JSON.readValue(value, CommandExecutionPort.TelnetPrompts.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static java.time.Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException { var timestamp = rs.getTimestamp(column); return timestamp == null ? null : timestamp.toInstant(); }
    public record Attempt(int number, String status, String leaseOwner, java.time.Instant leaseUntil, java.time.Instant startedAt, java.time.Instant finishedAt, String failureReason) { }
}
