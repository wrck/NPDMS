package com.dp.deviceops.parser.runtime.service;

import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParseResultEnvelope;
import com.dp.deviceops.parser.runtime.model.ParseTask;
import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParseResultQueryPort;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.runtime.port.WorkerCapabilityRepository;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.DefaultDynamicSemanticParser;
import com.dp.deviceops.parser.semantic.SemanticParseResult;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ParserReleaseManifest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class RuntimeServiceTestFixture implements ParserReleaseRepository, ParseTaskRepository,
        ParseResultQueryPort, WorkerCapabilityRepository {

    static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    static final String INPUT_FORMAT = "command-output-block/v1";

    final Map<String, ParserRelease> releases = new LinkedHashMap<>();
    final Map<String, ParserReleaseBundle> bundles = new HashMap<>();
    final Map<String, String> active = new HashMap<>();
    final Map<String, ParseTask> tasks = new LinkedHashMap<>();
    final Map<String, SubmissionIdentity> submissions = new HashMap<>();
    final Map<String, ParseResultEnvelope> results = new HashMap<>();
    long availableWorkers = 1;

    static ParserRelease draft(String releaseId, String logType, String version) {
        return new ParserRelease(releaseId, logType, version, ReleaseState.DRAFT,
                coordinate(logType, version), 1, null, NOW, null);
    }

    static ParserReleaseBundle bundle(String logType, String version) {
        ParserReleaseManifest manifest = new ParserReleaseManifest(
                "1.0.0", logType, version, INPUT_FORMAT, "1.0.0", "1.0.0",
                "1.0.0", "1.0.0", "1.0.0", null);
        String input = """
                {"schemaVersion":"1.0.0","commandBlocks":[{"commandIndex":1,
                "commandText":"event","status":"SUCCEEDED","stdout":"event","stderr":"",
                "receivedBytes":5,"pageCount":1,"truncated":false,"exitCode":0}]}
                """;
        ParserReleaseBundle provisional = new ParserReleaseBundle(manifest, rules(), projections(),
                List.of(new ParserReleaseBundle.VerificationCase("event", input, "{}")));
        String expected = new CanonicalJson().text(new DefaultDynamicSemanticParser().parse(
                new ParserPlanCompiler().compile(provisional), () -> new ByteArrayInputStream(
                        input.getBytes(StandardCharsets.UTF_8))));
        return new ParserReleaseBundle(manifest, rules(), projections(),
                List.of(new ParserReleaseBundle.VerificationCase("event", input, expected)));
    }

    ParserRelease addPublished(String releaseId, String logType, String version) {
        ParserReleaseValidation validation = new ParserReleaseValidation(
                releaseId, 1, true, 1, List.of(), NOW);
        ParserRelease release = new ParserRelease(releaseId, logType, version,
                ReleaseState.PUBLISHED, coordinate(logType, version), 1, validation, NOW, NOW);
        releases.put(releaseId, release);
        bundles.put(releaseId, bundle(logType, version));
        return release;
    }

    void addResult(String resultId, ParseTask sourceTask) {
        results.put(resultId, new ParseResultEnvelope(resultId, sourceTask.taskId(), sourceTask.releaseId(),
                sourceTask.coordinate(), sourceTask.contextSnapshot(), null, semanticResult(), NOW));
    }

    @Override
    public LogType createLogType(LogType logType) {
        return logType;
    }

    @Override
    public Optional<LogType> findLogType(String logType) {
        return Optional.empty();
    }

    @Override
    public List<LogType> listLogTypes() {
        return List.of();
    }

    @Override
    public ParserRelease saveDraft(ParserRelease release, ParserReleaseBundle bundle) {
        releases.put(release.releaseId(), release);
        bundles.put(release.releaseId(), bundle);
        return release;
    }

    @Override
    public Optional<ParserRelease> findRelease(String releaseId) {
        return Optional.ofNullable(releases.get(releaseId));
    }

    @Override
    public List<ParserRelease> listReleases(String logType) {
        return releases.values().stream().filter(item -> item.logType().equals(logType)).toList();
    }

    @Override
    public Optional<ParserRelease> findActive(String logType) {
        return Optional.ofNullable(active.get(logType)).map(releases::get);
    }

    @Override
    public ParserReleaseBundle loadBundle(String releaseId) {
        return Objects.requireNonNull(bundles.get(releaseId));
    }

    @Override
    public boolean saveValidation(ParserReleaseValidation validation) {
        ParserRelease release = releases.get(validation.releaseId());
        if (release == null || release.state() != ReleaseState.DRAFT
                || release.draftRevision() != validation.draftRevision()) {
            return false;
        }
        releases.put(release.releaseId(), new ParserRelease(release.releaseId(), release.logType(),
                release.releaseVersion(), release.state(), release.coordinate(), release.draftRevision(),
                validation, release.createdAt(), null));
        return true;
    }

    @Override
    public boolean publish(String releaseId, ReleaseState expectedState) {
        ParserRelease release = releases.get(releaseId);
        if (release == null || release.state() != expectedState) {
            return false;
        }
        releases.put(releaseId, new ParserRelease(release.releaseId(), release.logType(),
                release.releaseVersion(), ReleaseState.PUBLISHED, release.coordinate(), release.draftRevision(),
                release.validation(), release.createdAt(), NOW));
        return true;
    }

    @Override
    public boolean activate(String logType, String releaseId, String expectedCurrentReleaseId) {
        if (!Objects.equals(active.get(logType), expectedCurrentReleaseId)) {
            return false;
        }
        active.put(logType, releaseId);
        return true;
    }

    @Override
    public boolean clearActive(String logType, String expectedCurrentReleaseId) {
        if (!Objects.equals(active.get(logType), expectedCurrentReleaseId)) {
            return false;
        }
        active.remove(logType);
        return true;
    }

    @Override
    public boolean disable(String releaseId) {
        ParserRelease release = releases.get(releaseId);
        if (release == null || release.state() == ReleaseState.DRAFT) {
            return false;
        }
        releases.put(releaseId, new ParserRelease(release.releaseId(), release.logType(),
                release.releaseVersion(), ReleaseState.DISABLED, release.coordinate(), release.draftRevision(),
                release.validation(), release.createdAt(), release.publishedAt()));
        active.values().removeIf(releaseId::equals);
        return true;
    }

    @Override
    public synchronized Optional<SubmitOutcome> findSubmission(SubmissionIdentity request) {
        return tasks.values().stream().filter(task ->
                task.callerNamespace().equals(request.callerNamespace())
                        && task.requestId().equals(request.requestId())).findFirst().map(task -> {
            if (!request.equals(submissions.get(task.taskId()))) {
                throw new ParserRuntimeError("IDEMPOTENCY_CONFLICT");
            }
            return outcome(task);
        });
    }

    @Override
    public synchronized SubmitOutcome submit(SubmitRequest request) {
        var existing = findSubmission(request.identity());
        if (existing.isPresent()) {
            return existing.get();
        }
        ParseTask task = new ParseTask(request.taskId(), request.requestId(), request.callerNamespace(),
                request.logType(), request.releaseId(), request.coordinate(), request.inputFormat(),
                request.inputRef(), request.contextSnapshot(), request.sourceResultId(),
                request.resultConsumerId(), request.resultDestination(), ParseTaskState.QUEUED,
                null, 0, request.createdAt(), null, 0, null, null, request.createdAt(), request.createdAt());
        tasks.put(task.taskId(), task);
        submissions.put(task.taskId(), request.identity());
        return outcome(task);
    }

    @Override
    public SubmitOutcome retry(String taskId, String callerNamespace, Instant nextAttemptAt) {
        ParseTask task = tasks.get(taskId);
        if (task == null || !task.callerNamespace().equals(callerNamespace)) {
            throw new ParserRuntimeError("TASK_NOT_FOUND");
        }
        return outcome(task);
    }

    @Override public List<ClaimedTask> claim(String workerId, Set<WorkerCapability> capabilities,
            Instant now, Instant leaseUntil, int limit) { return List.of(); }
    @Override public void renew(String taskId, String workerId, long leaseGeneration, Instant leaseUntil) { }
    @Override public void releaseClaim(String taskId, String workerId, long leaseGeneration, Instant nextAttemptAt) { }
    @Override public void complete(Completion completion) { }
    @Override public void waitFor(Waiting waiting) { }
    @Override public void fail(Failure failure) { }
    @Override public void cancel(String taskId, String callerNamespace) { }
    @Override public void terminateWaiting(String taskId, String callerNamespace, String errorCode, Instant at) { }
    @Override public int recoverExpired(Instant now, int limit) { return 0; }

    @Override
    public Optional<ParseTask> findTask(String callerNamespace, String taskId) {
        return Optional.ofNullable(tasks.get(taskId))
                .filter(task -> task.callerNamespace().equals(callerNamespace));
    }

    @Override
    public List<ParseTask> listTasks(String callerNamespace, int limit, String afterTaskId) {
        return tasks.values().stream().filter(task -> task.callerNamespace().equals(callerNamespace))
                .limit(limit).toList();
    }

    @Override
    public Optional<ParseResultEnvelope> findResult(String callerNamespace, String resultId) {
        return Optional.ofNullable(results.get(resultId)).filter(result ->
                findTask(callerNamespace, result.taskId()).isPresent());
    }

    @Override
    public List<ParseTaskResult> listTaskResultsByRequestPrefix(String callerNamespace, String requestPrefix) {
        return tasks.values().stream()
                .filter(task -> task.callerNamespace().equals(callerNamespace))
                .filter(task -> task.requestId().startsWith(requestPrefix))
                .map(task -> new ParseTaskResult(task,
                        task.resultId() == null ? null : results.get(task.resultId())))
                .toList();
    }

    @Override public void heartbeat(String workerId, Set<WorkerCapability> capabilities, Instant expiresAt) { }
    @Override public long countAvailable(ParserCoordinate coordinate, Instant now) { return availableWorkers; }
    @Override public List<WorkerHeartbeat> listActive(Instant now) { return List.of(); }
    @Override public int reconcileWaiting(Instant now, int limit) { return 0; }

    private static SubmitOutcome outcome(ParseTask task) {
        return new SubmitOutcome(task.taskId(), task.state(), task.releaseId(),
                task.coordinate(), task.sourceResultId());
    }

    private static ParserCoordinate coordinate(String logType, String version) {
        return new ParserCoordinate(logType, version, "1.0.0", "1.0.0", "1.0.0", null, null);
    }

    private static SemanticParseResult semanticResult() {
        String hash = "0".repeat(64);
        return new SemanticParseResult("1.0.0", "1.0.0", "1.0.0", "1.0.0",
                hash, hash, hash, Map.of(), Map.of(), Map.of(), List.of());
    }

    private static String rules() {
        return """
                {"schemaVersion":"1.0.0","catalogVersion":"1.0.0","rules":[
                  {"ruleId":"event","blockRole":"EVENT","roleSelectors":[{"type":"CONTENT_REGEX","value":"event"}],
                   "extractors":[],"target":{"semanticKey":"event","cardinality":"ONE","dataType":"object","conflictPolicy":"HIGHEST_CONFIDENCE"}}
                ]}
                """;
    }

    private static String projections() {
        return """
                {"schemaVersion":"1.0.0","catalogVersion":"1.0.0","profiles":[]}
                """;
    }
}
