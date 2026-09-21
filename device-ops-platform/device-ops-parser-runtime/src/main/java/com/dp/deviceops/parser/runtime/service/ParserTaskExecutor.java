package com.dp.deviceops.parser.runtime.service;

import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.ClaimedTask;
import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.runtime.port.ParserRuntimeTelemetryPort;
import com.dp.deviceops.parser.semantic.DynamicSemanticParser;
import com.dp.deviceops.parser.semantic.SemanticParseResult;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Executes one already claimed task without choosing or changing its pinned release. */
public final class ParserTaskExecutor {

    private final String workerId;
    private final ParserReleaseRepository releases;
    private final ParserPayloadStore payloads;
    private final ParseTaskRepository tasks;
    private final ParserPlanCompiler compiler;
    private final DynamicSemanticParser parser;
    private final ParserPlanCache planCache;
    private final Clock clock;
    private final Duration retryDelay;
    private final ParserRuntimeTelemetryPort telemetry;

    public ParserTaskExecutor(String workerId, ParserReleaseRepository releases, ParserPayloadStore payloads,
            ParseTaskRepository tasks, ParserPlanCompiler compiler, DynamicSemanticParser parser,
            ParserPlanCache planCache, Clock clock, Duration retryDelay) {
        this(workerId, releases, payloads, tasks, compiler, parser, planCache, clock, retryDelay,
                ParserRuntimeTelemetryPort.NOOP);
    }

    public ParserTaskExecutor(String workerId, ParserReleaseRepository releases, ParserPayloadStore payloads,
            ParseTaskRepository tasks, ParserPlanCompiler compiler, DynamicSemanticParser parser,
            ParserPlanCache planCache, Clock clock, Duration retryDelay, ParserRuntimeTelemetryPort telemetry) {
        this.workerId = requireText(workerId, "workerId");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.payloads = Objects.requireNonNull(payloads, "payloads");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.planCache = Objects.requireNonNull(planCache, "planCache");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay must not be negative");
        }
    }

    public void execute(ClaimedTask task) {
        Objects.requireNonNull(task, "task");
        Instant startedAt = clock.instant();
        ParserPlan plan;
        try {
            plan = planCache.getOrLoad(task.releaseId(),
                    () -> compiler.compile(releases.loadBundle(task.releaseId())));
            if (!task.coordinate().equals(plan.coordinate())) {
                throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                        "task coordinate does not match its parser release");
            }
        } catch (SemanticParserError deterministicRulesFailure) {
            fail(task, deterministicRulesFailure.code(), deterministicRulesFailure.getMessage(), startedAt);
            return;
        } catch (RuntimeException artifactUnavailable) {
            telemetry.releaseLoadFailed(task.coordinate(), artifactUnavailable.getClass().getSimpleName());
            waitFor(task, ParseWaitReason.ARTIFACT_UNAVAILABLE, "PARSER_ARTIFACT_UNAVAILABLE", startedAt);
            return;
        }

        SemanticParseResult result;
        try {
            result = parser.parse(plan, payloads.open(task.inputRef()));
        } catch (SemanticParserError deterministicInputFailure) {
            if (SemanticParserError.INPUT_UNAVAILABLE.equals(deterministicInputFailure.code())) {
                waitFor(task, ParseWaitReason.PAYLOAD_UNAVAILABLE, "PARSER_PAYLOAD_UNAVAILABLE", startedAt);
            } else {
                fail(task, deterministicInputFailure.code(), deterministicInputFailure.getMessage(), startedAt);
            }
            return;
        } catch (RuntimeException payloadUnavailable) {
            waitFor(task, ParseWaitReason.PAYLOAD_UNAVAILABLE, "PARSER_PAYLOAD_UNAVAILABLE", startedAt);
            return;
        }

        try {
            tasks.complete(new ParseTaskRepository.Completion(task.taskId(), workerId,
                    task.leaseGeneration(), result, clock.instant()));
            telemetry.taskSucceeded(task.coordinate(), elapsed(startedAt), unmappedUnits(result));
        } catch (ParserRuntimeError error) {
            if ("PARSER_LEASE_LOST".equals(error.code())) return;
            waitFor(task, ParseWaitReason.TRANSIENT_STORAGE_ERROR, "PARSER_RESULT_STORAGE_UNAVAILABLE", startedAt);
        } catch (RuntimeException storageUnavailable) {
            waitFor(task, ParseWaitReason.TRANSIENT_STORAGE_ERROR, "PARSER_RESULT_STORAGE_UNAVAILABLE", startedAt);
        }
    }

    private void waitFor(ClaimedTask task, ParseWaitReason reason, String errorCode, Instant startedAt) {
        Instant retryAt = clock.instant().plus(retryDelay);
        try {
            tasks.waitFor(new ParseTaskRepository.Waiting(task.taskId(), workerId,
                    task.leaseGeneration(), reason, retryAt, errorCode));
            telemetry.taskWaiting(task.coordinate(), reason, errorCode, elapsed(startedAt));
        } catch (ParserRuntimeError error) {
            if (!"PARSER_LEASE_LOST".equals(error.code())) {
                throw error;
            }
        }
    }

    private void fail(ClaimedTask task, String errorCode, String message, Instant startedAt) {
        try {
            tasks.fail(new ParseTaskRepository.Failure(task.taskId(), workerId,
                    task.leaseGeneration(), errorCode, message, clock.instant()));
            telemetry.taskFailed(task.coordinate(), errorCode, elapsed(startedAt));
        } catch (ParserRuntimeError error) {
            if (!"PARSER_LEASE_LOST".equals(error.code())) {
                throw error;
            }
        }
    }

    private Duration elapsed(Instant startedAt) {
        Duration elapsed = Duration.between(startedAt, clock.instant());
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }

    private static long unmappedUnits(SemanticParseResult result) {
        Object value = result.quality().get("unparsedBlockCount");
        return value instanceof Number number ? Math.max(0, number.longValue()) : 0;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
