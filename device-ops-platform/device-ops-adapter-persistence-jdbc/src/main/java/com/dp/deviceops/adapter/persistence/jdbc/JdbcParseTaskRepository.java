package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class JdbcParseTaskRepository implements ParseTaskRepository {

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final ParserJdbcJson json;
    private final CanonicalJson canonicalJson = new CanonicalJson();
    private final Supplier<String> resultIds;
    private final Supplier<String> payloadIds;
    private final Supplier<String> eventIds;

    public JdbcParseTaskRepository(JdbcClient jdbc, TransactionTemplate transactions, ObjectMapper objectMapper,
            Supplier<String> resultIds, Supplier<String> payloadIds, Supplier<String> eventIds) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.json = new ParserJdbcJson(Objects.requireNonNull(objectMapper, "objectMapper"));
        this.resultIds = Objects.requireNonNull(resultIds, "resultIds");
        this.payloadIds = Objects.requireNonNull(payloadIds, "payloadIds");
        this.eventIds = Objects.requireNonNull(eventIds, "eventIds");
    }

    @Override
    public Optional<SubmitOutcome> findSubmission(SubmissionIdentity request) {
        return jdbc.sql("select * from device_ops_parse_task "
                        + "where caller_namespace=:namespace and request_id=:request")
                .param("namespace", request.callerNamespace()).param("request", request.requestId())
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new ColumnMapRowMapper().mapRow(rs, rowNum);
                    // getString materializes CLOBs consistently for H2 and MySQL.
                    row.put("CONTEXT_JSON", rs.getString("context_json"));
                    return row;
                }).optional().map(existing -> {
                    boolean recorded = Boolean.TRUE.equals(existing.get("REQUESTED_RELEASE_RECORDED"))
                            || existing.get("REQUESTED_RELEASE_RECORDED") instanceof Number number
                            && number.intValue() != 0;
                    boolean releaseMatches = recorded
                            ? Objects.equals(existing.get("REQUESTED_RELEASE_ID"), request.requestedReleaseId())
                            : request.requestedReleaseId() == null
                                    || Objects.equals(existing.get("RELEASE_ID"), request.requestedReleaseId());
                    if (!Objects.equals(existing.get("CALLER_NAMESPACE"), request.callerNamespace())
                            || !Objects.equals(existing.get("REQUEST_ID"), request.requestId())
                            || !Objects.equals(existing.get("LOG_TYPE"), request.logType())
                            || !releaseMatches
                            || !Objects.equals(existing.get("INPUT_FORMAT"), request.inputFormat())
                            || !Objects.equals(existing.get("INPUT_PAYLOAD_ID"), request.inputRef())
                            || !Objects.equals(existing.get("SOURCE_RESULT_ID"), request.sourceResultId())
                            || !Objects.equals(existing.get("RESULT_CONSUMER_ID"), request.resultConsumerId())
                            || !json.decode((String) existing.get("CONTEXT_JSON"),
                                    com.fasterxml.jackson.databind.JsonNode.class)
                                    .equals(json.decode(json.encode(request.contextSnapshot()),
                                            com.fasterxml.jackson.databind.JsonNode.class))) {
                        throw new ParserRuntimeError("IDEMPOTENCY_CONFLICT");
                    }
                    return outcome(existing);
                });
    }

    @Override
    public SubmitOutcome submit(SubmitRequest request) {
        Optional<SubmitOutcome> existing = findSubmission(request.identity());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            ParserCoordinate coordinate = request.coordinate();
            jdbc.sql("""
                    insert into device_ops_parse_task(
                      task_id,request_id,caller_namespace,log_type,release_id,release_version,engine_version,
                      rule_version,projection_version,extension_id,extension_version,input_format,input_payload_id,
                      context_json,source_result_id,result_consumer_id,result_destination,state,attempt_count,
                      next_attempt_at,lease_generation,created_at,updated_at,requested_release_id,requested_release_recorded)
                    values(:task,:request,:namespace,:type,:release,:releaseVersion,:engine,:rule,:projection,
                      :extensionId,:extensionVersion,:format,:input,:context,:source,:consumer,:destination,
                      'QUEUED',0,:created,0,:created,:created,:requestedRelease,true)
                    """).param("task", request.taskId()).param("request", request.requestId())
                    .param("namespace", request.callerNamespace()).param("type", request.logType())
                    .param("release", request.releaseId()).param("releaseVersion", coordinate.releaseVersion())
                    .param("engine", coordinate.engineVersion()).param("rule", coordinate.ruleVersion())
                    .param("projection", coordinate.projectionVersion()).param("extensionId", coordinate.extensionId())
                    .param("extensionVersion", coordinate.extensionVersion()).param("format", request.inputFormat())
                    .param("input", request.inputRef()).param("context", json.encode(request.contextSnapshot()))
                    .param("source", request.sourceResultId()).param("consumer", request.resultConsumerId())
                    .param("destination", request.resultDestination() == null ? null : request.resultDestination().toString())
                    .param("created", request.createdAt()).param("requestedRelease", request.requestedReleaseId()).update();
            return new SubmitOutcome(request.taskId(), ParseTaskState.QUEUED, request.releaseId(),
                    request.coordinate(), request.sourceResultId());
        } catch (DuplicateKeyException exception) {
            // A concurrent submit won the namespace/request unique key. Compare original intent,
            // not the active release or callback destination resolved by the losing request.
            // Suspend a caller's repeatable-read snapshot: the winning transaction has committed,
            // but H2/MySQL snapshot reads in the losing transaction may still not see that row.
            TransactionTemplate currentRead = new TransactionTemplate(
                    Objects.requireNonNull(transactions.getTransactionManager()));
            currentRead.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            currentRead.setReadOnly(true);
            return currentRead.execute(status -> findSubmission(request.identity()).orElseThrow(() -> exception));
        }
    }

    @Override
    public List<ClaimedTask> claim(String workerId, Set<WorkerCapability> capabilities,
            Instant now, Instant leaseUntil, int limit) {
        return transactions.execute(status -> {
            List<Map<String, Object>> candidates = jdbc.sql("select * from device_ops_parse_task "
                            + "where state='QUEUED' and next_attempt_at<=:now "
                            + "order by created_at,task_id limit :limit for update skip locked")
                    .param("now", now).param("limit", Math.max(limit * 4, limit)).query().listOfRows();
            List<ClaimedTask> claimed = new ArrayList<>();
            for (Map<String, Object> row : candidates) {
                ParserCoordinate coordinate = coordinate(row);
                if (!supports(capabilities, coordinate) || claimed.size() >= limit) {
                    continue;
                }
                int changed = jdbc.sql("update device_ops_parse_task set state='RUNNING',wait_reason=null," +
                                "lease_owner=:worker,lease_generation=lease_generation+1,lease_expires_at=:until," +
                                "attempt_count=attempt_count+1,updated_at=:now where task_id=:task and state='QUEUED'")
                        .param("worker", workerId).param("until", leaseUntil).param("now", now)
                        .param("task", row.get("TASK_ID")).update();
                if (changed == 1) {
                    Map<String, Object> current = jdbc.sql("select * from device_ops_parse_task where task_id=:task")
                            .param("task", row.get("TASK_ID")).query().singleRow();
                    jdbc.sql("insert into device_ops_parse_attempt(task_id,attempt_no,worker_id,lease_generation," +
                                    "started_at,status) values(:task,:attempt,:worker,:generation,:started,'RUNNING')")
                            .param("task", current.get("TASK_ID")).param("attempt", current.get("ATTEMPT_COUNT"))
                            .param("worker", workerId).param("generation", current.get("LEASE_GENERATION"))
                            .param("started", now).update();
                    claimed.add(new ClaimedTask((String) current.get("TASK_ID"), (String) current.get("RELEASE_ID"),
                            coordinate(current), (String) current.get("INPUT_PAYLOAD_ID"),
                            ((Number) current.get("LEASE_GENERATION")).longValue()));
                }
            }
            return claimed;
        });
    }

    @Override
    public void renew(String taskId, String workerId, long leaseGeneration, Instant leaseUntil) {
        changed(jdbc.sql("update device_ops_parse_task set lease_expires_at=:until,updated_at=current_timestamp "
                        + "where task_id=:task and state='RUNNING' and lease_owner=:worker and lease_generation=:generation")
                .param("until", leaseUntil).param("task", taskId).param("worker", workerId)
                .param("generation", leaseGeneration).update(), "PARSER_LEASE_LOST");
    }

    @Override
    public void releaseClaim(String taskId, String workerId, long leaseGeneration, Instant nextAttemptAt) {
        changed(jdbc.sql("update device_ops_parse_task set state='QUEUED',lease_owner=null,lease_expires_at=null," +
                        "next_attempt_at=:next,updated_at=current_timestamp where task_id=:task and state='RUNNING' " +
                        "and lease_owner=:worker and lease_generation=:generation")
                .param("next", nextAttemptAt).param("task", taskId).param("worker", workerId)
                .param("generation", leaseGeneration).update(), "PARSER_LEASE_LOST");
    }

    @Override
    public void complete(Completion completion) {
        transactions.executeWithoutResult(status -> {
            Map<String, Object> task = jdbc.sql("select * from device_ops_parse_task where task_id=:task")
                    .param("task", completion.taskId()).query().singleRow();
            if (!leaseMatches(task, completion.workerId(), completion.leaseGeneration())) {
                throw new ParserRuntimeError("PARSER_LEASE_LOST");
            }
            String resultId = resultIds.get();
            String payloadId = payloadIds.get();
            jdbc.sql("insert into device_ops_parser_payload(payload_id,media_type,content,created_at) "
                            + "values(:id,'application/json',:content,:created)")
                    .param("id", payloadId).param("content", new String(canonicalJson.bytes(completion.result()),
                            java.nio.charset.StandardCharsets.UTF_8)).param("created", completion.completedAt()).update();
            jdbc.sql("insert into device_ops_parse_result(result_id,task_id,release_id,coordinate_json,context_json," +
                            "source_result_id,structured_output_payload_id,created_at) " +
                            "values(:result,:task,:release,:coordinate,:context,:source,:payload,:created)")
                    .param("result", resultId).param("task", completion.taskId()).param("release", task.get("RELEASE_ID"))
                    .param("coordinate", json.encode(coordinate(task))).param("context", task.get("CONTEXT_JSON"))
                    .param("source", task.get("SOURCE_RESULT_ID")).param("payload", payloadId)
                    .param("created", completion.completedAt()).update();
            if (task.get("RESULT_DESTINATION") != null) {
                jdbc.sql("insert into device_ops_outbox(event_id,aggregate_id,event_type,payload,attempt_count," +
                                "next_attempt_at,destination) values(:event,:task,'PARSER_RESULT_READY',:payload,0,:next,:destination)")
                        .param("event", eventIds.get()).param("task", completion.taskId())
                        .param("payload", json.encode(Map.of("taskId", completion.taskId(), "resultId", resultId,
                                "status", "SUCCEEDED"))).param("next", completion.completedAt())
                        .param("destination", task.get("RESULT_DESTINATION")).update();
            }
            int updated = jdbc.sql("update device_ops_parse_task set state='SUCCEEDED',result_id=:result," +
                            "lease_owner=null,lease_expires_at=null,updated_at=:completed where task_id=:task " +
                            "and state='RUNNING' and lease_owner=:worker and lease_generation=:generation")
                    .param("result", resultId).param("completed", completion.completedAt())
                    .param("task", completion.taskId()).param("worker", completion.workerId())
                    .param("generation", completion.leaseGeneration()).update();
            if (updated != 1) {
                throw new ParserRuntimeError("PARSER_LEASE_LOST");
            }
            jdbc.sql("update device_ops_parse_attempt set status='SUCCEEDED',finished_at=:finished " +
                            "where task_id=:task and lease_generation=:generation")
                    .param("finished", completion.completedAt()).param("task", completion.taskId())
                    .param("generation", completion.leaseGeneration()).update();
        });
    }

    @Override
    public void waitFor(Waiting waiting) {
        terminalLeaseUpdate(waiting.taskId(), waiting.workerId(), waiting.leaseGeneration(), "WAITING",
                waiting.reason().name(), waiting.nextAttemptAt(), waiting.errorCode(), null);
    }

    @Override
    public void fail(Failure failure) {
        terminalLeaseUpdate(failure.taskId(), failure.workerId(), failure.leaseGeneration(), "FAILED",
                null, failure.failedAt(), failure.errorCode(), failure.message());
    }

    @Override
    public void cancel(String taskId, String callerNamespace) {
        int changed = jdbc.sql("update device_ops_parse_task set state='CANCELLED',lease_owner=null," +
                        "lease_expires_at=null,updated_at=current_timestamp where task_id=:task and caller_namespace=:namespace " +
                        "and state in ('QUEUED','WAITING','RUNNING')")
                .param("task", taskId).param("namespace", callerNamespace).update();
        changed(changed, "TASK_NOT_CANCELLABLE");
    }

    @Override
    public SubmitOutcome retry(String taskId, String callerNamespace, Instant nextAttemptAt) {
        changed(jdbc.sql("update device_ops_parse_task set state='QUEUED',wait_reason=null,error_code=null," +
                        "error_message=null,next_attempt_at=:next,updated_at=:next where task_id=:task " +
                        "and caller_namespace=:namespace and state in ('FAILED','WAITING')")
                .param("next", nextAttemptAt).param("task", taskId).param("namespace", callerNamespace).update(),
                "TASK_NOT_RETRYABLE");
        return outcome(jdbc.sql("select * from device_ops_parse_task where task_id=:task")
                .param("task", taskId).query().singleRow());
    }

    @Override
    public void terminateWaiting(String taskId, String callerNamespace, String errorCode, Instant at) {
        changed(jdbc.sql("update device_ops_parse_task set state='FAILED',wait_reason=null,error_code=:error," +
                        "updated_at=:at where task_id=:task and caller_namespace=:namespace and state='WAITING'")
                .param("error", errorCode).param("at", at).param("task", taskId)
                .param("namespace", callerNamespace).update(), "TASK_NOT_WAITING");
    }

    @Override
    public int recoverExpired(Instant now, int limit) {
        List<String> ids = jdbc.sql("select task_id from device_ops_parse_task where state='RUNNING' " +
                        "and lease_expires_at<:now order by lease_expires_at limit :limit")
                .param("now", now).param("limit", limit).query(String.class).list();
        int recovered = 0;
        for (String id : ids) {
            recovered += jdbc.sql("update device_ops_parse_task set state='QUEUED',lease_owner=null," +
                            "lease_expires_at=null,next_attempt_at=:now,updated_at=:now where task_id=:task " +
                            "and state='RUNNING' and lease_expires_at<:now")
                    .param("now", now).param("task", id).update();
        }
        return recovered;
    }

    private void terminalLeaseUpdate(String taskId, String workerId, long generation, String state,
            String reason, Instant at, String errorCode, String errorMessage) {
        changed(jdbc.sql("update device_ops_parse_task set state=:state,wait_reason=:reason,error_code=:error," +
                        "error_message=:message,next_attempt_at=:at,lease_owner=null,lease_expires_at=null,updated_at=:at " +
                        "where task_id=:task and state='RUNNING' and lease_owner=:worker and lease_generation=:generation")
                .param("state", state).param("reason", reason).param("error", errorCode).param("message", errorMessage)
                .param("at", at).param("task", taskId).param("worker", workerId).param("generation", generation)
                .update(), "PARSER_LEASE_LOST");
    }

    private static boolean supports(Set<WorkerCapability> capabilities, ParserCoordinate coordinate) {
        return capabilities.stream().anyMatch(capability -> capability.engineVersion().equals(coordinate.engineVersion())
                && Objects.equals(capability.extensionId(), coordinate.extensionId())
                && Objects.equals(capability.extensionVersion(), coordinate.extensionVersion()));
    }

    private static boolean leaseMatches(Map<String, Object> task, String worker, long generation) {
        return "RUNNING".equals(task.get("STATE")) && Objects.equals(worker, task.get("LEASE_OWNER"))
                && ((Number) task.get("LEASE_GENERATION")).longValue() == generation;
    }

    static ParserCoordinate coordinate(Map<String, Object> row) {
        return new ParserCoordinate((String) row.get("LOG_TYPE"), (String) row.get("RELEASE_VERSION"),
                (String) row.get("ENGINE_VERSION"), (String) row.get("RULE_VERSION"),
                (String) row.get("PROJECTION_VERSION"), (String) row.get("EXTENSION_ID"),
                (String) row.get("EXTENSION_VERSION"));
    }

    private static SubmitOutcome outcome(Map<String, Object> row) {
        return new SubmitOutcome((String) row.get("TASK_ID"), ParseTaskState.valueOf((String) row.get("STATE")),
                (String) row.get("RELEASE_ID"), coordinate(row), (String) row.get("SOURCE_RESULT_ID"));
    }

    private static void changed(int count, String code) {
        if (count != 1) {
            throw new ParserRuntimeError(code);
        }
    }
}
