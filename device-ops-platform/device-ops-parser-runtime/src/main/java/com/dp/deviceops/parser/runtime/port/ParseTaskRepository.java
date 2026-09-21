package com.dp.deviceops.parser.runtime.port;

import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.SemanticParseResult;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ParseTaskRepository {
    /** Returns the original pinned task, or throws IDEMPOTENCY_CONFLICT when its semantics differ. */
    Optional<SubmitOutcome> findSubmission(SubmissionIdentity request);

    /** Atomically inserts or replays using the same comparison as findSubmission. */
    SubmitOutcome submit(SubmitRequest request);

    List<ClaimedTask> claim(String workerId, Set<WorkerCapability> capabilities,
            Instant now, Instant leaseUntil, int limit);

    void renew(String taskId, String workerId, long leaseGeneration, Instant leaseUntil);

    void releaseClaim(String taskId, String workerId, long leaseGeneration, Instant nextAttemptAt);

    void complete(Completion completion);

    void waitFor(Waiting waiting);

    void fail(Failure failure);

    void cancel(String taskId, String callerNamespace);

    SubmitOutcome retry(String taskId, String callerNamespace, Instant nextAttemptAt);

    void terminateWaiting(String taskId, String callerNamespace, String errorCode, Instant at);

    int recoverExpired(Instant now, int limit);

    record SubmitRequest(
            String taskId,
            String requestId,
            String callerNamespace,
            String logType,
            String releaseId,
            ParserCoordinate coordinate,
            String inputFormat,
            String inputRef,
            Map<String, Object> contextSnapshot,
            String sourceResultId,
            String resultConsumerId,
            URI resultDestination,
            Instant createdAt,
            String requestedReleaseId) {
        public SubmitRequest {
            contextSnapshot = contextSnapshot == null ? Map.of() : Map.copyOf(contextSnapshot);
        }

        /** Existing internal callers supply an explicitly pinned release. */
        public SubmitRequest(String taskId, String requestId, String callerNamespace, String logType,
                String releaseId, ParserCoordinate coordinate, String inputFormat, String inputRef,
                Map<String, Object> contextSnapshot, String sourceResultId, String resultConsumerId,
                URI resultDestination, Instant createdAt) {
            this(taskId, requestId, callerNamespace, logType, releaseId, coordinate, inputFormat, inputRef,
                    contextSnapshot, sourceResultId, resultConsumerId, resultDestination, createdAt, releaseId);
        }

        public SubmissionIdentity identity() {
            return new SubmissionIdentity(requestId, callerNamespace, logType, requestedReleaseId,
                    inputFormat, inputRef, contextSnapshot, sourceResultId, resultConsumerId);
        }
    }

    /** Original caller intent; a null requested release means active-release selection, not the resolved pin. */
    record SubmissionIdentity(String requestId, String callerNamespace, String logType,
            String requestedReleaseId, String inputFormat, String inputRef,
            Map<String, Object> contextSnapshot, String sourceResultId, String resultConsumerId) {
        public SubmissionIdentity {
            contextSnapshot = contextSnapshot == null ? Map.of() : Map.copyOf(contextSnapshot);
        }
    }

    record SubmitOutcome(String taskId, ParseTaskState state, String releaseId,
            ParserCoordinate coordinate, String sourceResultId) {
    }

    record ClaimedTask(String taskId, String releaseId, ParserCoordinate coordinate,
            String inputRef, long leaseGeneration) {
    }

    record Completion(String taskId, String workerId, long leaseGeneration,
            SemanticParseResult result, Instant completedAt) {
    }

    record Waiting(String taskId, String workerId, long leaseGeneration,
            ParseWaitReason reason, Instant nextAttemptAt, String errorCode) {
    }

    record Failure(String taskId, String workerId, long leaseGeneration,
            String errorCode, String message, Instant failedAt) {
    }
}
