package com.dp.deviceops.parser.runtime.service;

import com.dp.deviceops.parser.runtime.model.ParseResultEnvelope;
import com.dp.deviceops.parser.runtime.model.ParseTask;
import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.port.ParseResultQueryPort;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.SubmitOutcome;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.SubmitRequest;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;

import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class ParseTaskService {

    private final ParserReleaseRepository releases;
    private final ParseTaskRepository tasks;
    private final ParseResultQueryPort results;
    private final ResultConsumerRegistry resultConsumers;
    private final Clock clock;
    private final Supplier<String> taskIds;

    public ParseTaskService(ParserReleaseRepository releases, ParseTaskRepository tasks,
            ParseResultQueryPort results, ResultConsumerRegistry resultConsumers,
            Clock clock, Supplier<String> taskIds) {
        this.releases = Objects.requireNonNull(releases, "releases");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.results = Objects.requireNonNull(results, "results");
        this.resultConsumers = Objects.requireNonNull(resultConsumers, "resultConsumers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.taskIds = Objects.requireNonNull(taskIds, "taskIds");
    }

    public SubmitOutcome submit(SubmitCommand command) {
        Objects.requireNonNull(command, "command");
        var existing = tasks.findSubmission(new ParseTaskRepository.SubmissionIdentity(
                command.requestId(), command.callerNamespace(), command.logType(), command.releaseId(),
                command.inputFormat(), command.inputRef(), command.contextSnapshot(),
                command.sourceResultId(), command.resultConsumerId()));
        if (existing.isPresent()) {
            return existing.get();
        }
        ParserRelease release = command.releaseId() == null
                ? releases.findActive(command.logType())
                        .orElseThrow(() -> error("ACTIVE_RELEASE_NOT_FOUND", "active release was not found"))
                : requirePublished(command.logType(), command.releaseId());
        if (release.state() != ReleaseState.PUBLISHED) {
            throw error("RELEASE_NOT_PUBLISHED", "release is not available for new tasks");
        }
        String expectedFormat = releases.loadBundle(release.releaseId()).manifest().inputAdapter();
        if (!expectedFormat.equals(command.inputFormat())) {
            throw error("INPUT_FORMAT_MISMATCH", "input format does not match the parser release");
        }
        URI destination = command.resultConsumerId() == null ? null
                : resultConsumers.findDestination(command.resultConsumerId())
                        .orElseThrow(() -> error("RESULT_CONSUMER_NOT_FOUND", "result consumer was not found"));
        SubmitRequest request = new SubmitRequest(taskIds.get(), command.requestId(),
                command.callerNamespace(), command.logType(), release.releaseId(), release.coordinate(),
                command.inputFormat(), command.inputRef(), command.contextSnapshot(), command.sourceResultId(),
                command.resultConsumerId(), destination, clock.instant(), command.releaseId());
        return tasks.submit(request);
    }

    public SubmitOutcome reparse(String callerNamespace, String sourceResultId,
            String releaseId, String requestId) {
        ParseResultEnvelope sourceResult = results.findResult(callerNamespace, sourceResultId)
                .orElseThrow(() -> error("RESULT_NOT_FOUND", "source result was not found"));
        ParseTask sourceTask = results.findTask(callerNamespace, sourceResult.taskId())
                .orElseThrow(() -> error("TASK_NOT_FOUND", "source task was not found"));
        return submit(new SubmitCommand(requestId, callerNamespace, sourceTask.logType(), releaseId,
                sourceTask.inputFormat(), sourceTask.inputRef(), sourceTask.contextSnapshot(),
                sourceResultId, sourceTask.resultConsumerId()));
    }

    public SubmitOutcome retry(String callerNamespace, String taskId) {
        return tasks.retry(taskId, callerNamespace, clock.instant());
    }

    public void cancel(String callerNamespace, String taskId) {
        tasks.cancel(taskId, callerNamespace);
    }

    public void terminateWaiting(String callerNamespace, String taskId) {
        tasks.terminateWaiting(taskId, callerNamespace, "OPERATOR_TERMINATED", clock.instant());
    }

    private ParserRelease requirePublished(String logType, String releaseId) {
        ParserRelease release = releases.findRelease(releaseId)
                .orElseThrow(() -> error("RELEASE_NOT_FOUND", "parser release was not found"));
        if (!release.logType().equals(logType) || release.state() != ReleaseState.PUBLISHED) {
            throw error("RELEASE_NOT_PUBLISHED", "release is not published for the requested log type");
        }
        return release;
    }

    private static ParserRuntimeError error(String code, String message) {
        return new ParserRuntimeError(code, message);
    }

    public record SubmitCommand(
            String requestId,
            String callerNamespace,
            String logType,
            String releaseId,
            String inputFormat,
            String inputRef,
            Map<String, Object> contextSnapshot,
            String sourceResultId,
            String resultConsumerId) {
        public SubmitCommand {
            contextSnapshot = contextSnapshot == null ? Map.of() : Map.copyOf(contextSnapshot);
        }
    }
}
