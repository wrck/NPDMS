package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.parser.ParserControlProperties;
import com.dp.deviceops.core.model.*;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.core.port.CollectionTargetReferencePort;
import com.dp.deviceops.core.service.CollectionWorker;
import com.dp.deviceops.core.service.OutputParserRegistry;
import com.dp.deviceops.core.service.SubmitCollectionService;
import com.dp.deviceops.parser.runtime.service.ParserReleaseService;
import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
import com.dp.deviceops.parser.runtime.service.ResultConsumerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Shared persistence and dispatch path for generic and project-scoped submissions. */
@Component
public final class CollectionSubmissionCoordinator {
    private final SubmitCollectionService submissions;
    private final CollectionTargetReferencePort targets;
    private final CollectionExecutionCoordinator executionCoordinator;
    private final CollectionExecutionPersistencePort execution;
    private final OutputParserRegistry parsers;
    private final ParserReleaseService parserReleases;
    private final ResultConsumerRegistry resultConsumers;
    private final ParserControlProperties parserProperties;

    @Autowired
    public CollectionSubmissionCoordinator(SubmitCollectionService submissions, CollectionTargetReferencePort targets,
                                           CollectionExecutionCoordinator executionCoordinator,
                                           CollectionExecutionPersistencePort execution, OutputParserRegistry parsers,
                                           ParserReleaseService parserReleases,
                                           ResultConsumerRegistry resultConsumers,
                                           ParserControlProperties parserProperties) {
        this.submissions = submissions;
        this.targets = targets;
        this.executionCoordinator = executionCoordinator;
        this.execution = execution;
        this.parsers = parsers;
        this.parserReleases = parserReleases;
        this.resultConsumers = resultConsumers;
        this.parserProperties = parserProperties;
    }

    public CollectionSubmissionCoordinator(SubmitCollectionService submissions, CollectionTargetReferencePort targets,
                                           CollectionExecutionCoordinator executionCoordinator,
                                           CollectionExecutionPersistencePort execution, OutputParserRegistry parsers,
                                           ParserReleaseService parserReleases,
                                           ResultConsumerRegistry resultConsumers) {
        this(submissions, targets, executionCoordinator, execution, parsers, parserReleases, resultConsumers,
                disabledAutomaticParsing());
    }

    public Optional<Result> findExisting(String namespace, String idempotencyKey, String fingerprint) {
        return submissions.findExistingId(namespace, idempotencyKey, fingerprint)
                .map(id -> new Result(id, true));
    }

    public Result submit(Command command) {
        List<ExecutionConnectionContext> contexts = command.targets().stream().map(PreparedTarget::connection).toList();
        int submitted = 0;
        try {
            Optional<String> existingId = command.submissionFingerprint() == null
                    ? submissions.findExistingId(command.namespace(), command.idempotencyKey())
                    : submissions.findExistingId(command.namespace(), command.idempotencyKey(), command.submissionFingerprint());
            if (existingId.isPresent()) {
                contexts.forEach(ExecutionConnectionContext::close);
                return new Result(existingId.get(), true);
            }
            CollectionSemanticParsing semanticParsing = resolveSemanticParsing(command.semanticParsing());
            var result = submissions.submit(new SubmitCollectionService.SubmitCollectionCommand(
                    command.namespace(), command.projectKey(), command.externalRequestId(), command.idempotencyKey(),
                    command.targets().stream().map(PreparedTarget::snapshot).toList(), command.script(),
                    command.activityType(), command.callbackUrl() == null ? null : URI.create(command.callbackUrl()),
                    semanticParsing, command.submissionFingerprint(), command.submissionSnapshot()));
            if (result.existing()) {
                contexts.forEach(ExecutionConnectionContext::close);
                return new Result(result.collectionId(), true);
            }
            List<Long> ids = targets.findTargetIds(result.collectionId());
            if (ids.size() != contexts.size()) {
                throw new IllegalStateException("persisted target count differs");
            }
            for (int index = 0; index < ids.size(); index++) {
                try {
                    executionCoordinator.submit(new CollectionWorker.WorkItem(ids.get(index), UUID.randomUUID().toString(),
                            contexts.get(index), command.script(), command.commandTimeout(), command.parseTimeout(),
                            command.leaseGrace(), parsers));
                    submitted++;
                } catch (CollectionQueueFullException full) {
                    for (int remaining = index + 1; remaining < contexts.size(); remaining++) {
                        contexts.get(remaining).close();
                    }
                    execution.failUnclaimedTargets(ids.subList(index, ids.size()), "QUEUE_FULL");
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "collection queue is full", full);
                }
            }
            return new Result(result.collectionId(), false);
        } catch (RuntimeException failure) {
            for (int index = submitted; index < contexts.size(); index++) {
                contexts.get(index).close();
            }
            throw failure;
        }
    }

    private CollectionSemanticParsing resolveSemanticParsing(SemanticParsing requested) {
        boolean automatic = requested == null;
        if (automatic) {
            if (!parserProperties.isAutomaticParsingEnabled()) return null;
            requested = new SemanticParsing(true, parserProperties.getDefaultLogType(), null,
                    "command-output-block/v1", null);
        } else if (!requested.enabled()) {
            return null;
        }
        if (!"command-output-block/v1".equals(requested.inputFormat())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "collection semantic parsing requires command-output-block/v1");
        }
        try {
            var release = parserReleases.resolveForSubmission(
                    requested.logType(), requested.releaseId(), requested.inputFormat());
            URI destination = requested.resultConsumerId() == null ? null
                    : resultConsumers.findDestination(requested.resultConsumerId())
                            .orElseThrow(() -> new ParserRuntimeError("UNKNOWN_RESULT_CONSUMER",
                                    "result consumer is not configured"));
            return new CollectionSemanticParsing(requested.logType(), release.releaseId(),
                    requested.inputFormat(), requested.resultConsumerId(), destination);
        } catch (ParserRuntimeError invalidSelection) {
            if (automatic && "ACTIVE_RELEASE_NOT_FOUND".equals(invalidSelection.code())) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidSelection.getMessage(), invalidSelection);
        }
    }

    private static ParserControlProperties disabledAutomaticParsing() {
        var properties = new ParserControlProperties();
        properties.setAutomaticParsingEnabled(false);
        return properties;
    }

    public record PreparedTarget(CollectionTarget snapshot, ExecutionConnectionContext connection) {
        public PreparedTarget {
            Objects.requireNonNull(snapshot);
            Objects.requireNonNull(connection);
        }
    }

    public record Command(String namespace, String projectKey, String externalRequestId, String idempotencyKey,
                          List<PreparedTarget> targets, ScriptArtifact script, String activityType, String callbackUrl,
                          Duration commandTimeout, Duration parseTimeout, Duration leaseGrace,
                          SemanticParsing semanticParsing, String submissionFingerprint, String submissionSnapshot) {
        public Command(String namespace, String projectKey, String externalRequestId, String idempotencyKey,
                List<PreparedTarget> targets, ScriptArtifact script, String activityType, String callbackUrl,
                Duration commandTimeout, Duration parseTimeout, Duration leaseGrace, SemanticParsing semanticParsing,
                String submissionFingerprint) {
            this(namespace, projectKey, externalRequestId, idempotencyKey, targets, script, activityType,
                    callbackUrl, commandTimeout, parseTimeout, leaseGrace, semanticParsing, submissionFingerprint, null);
        }
        public Command(String namespace, String projectKey, String externalRequestId, String idempotencyKey,
                List<PreparedTarget> targets, ScriptArtifact script, String activityType, String callbackUrl,
                Duration commandTimeout, Duration parseTimeout, Duration leaseGrace, SemanticParsing semanticParsing) {
            this(namespace, projectKey, externalRequestId, idempotencyKey, targets, script, activityType,
                    callbackUrl, commandTimeout, parseTimeout, leaseGrace, semanticParsing, null);
        }
        public Command(String namespace, String projectKey, String externalRequestId, String idempotencyKey,
                List<PreparedTarget> targets, ScriptArtifact script, String activityType, String callbackUrl,
                Duration commandTimeout, Duration parseTimeout, Duration leaseGrace) {
            this(namespace, projectKey, externalRequestId, idempotencyKey, targets, script, activityType,
                    callbackUrl, commandTimeout, parseTimeout, leaseGrace, null);
        }
        public Command {
            targets = List.copyOf(targets);
        }
    }

    public record SemanticParsing(boolean enabled, String logType, String releaseId, String inputFormat,
                                  String resultConsumerId) {
        public SemanticParsing(String logType, String releaseId, String inputFormat, String resultConsumerId) {
            this(true, logType, releaseId, inputFormat, resultConsumerId);
        }
        public SemanticParsing {
            releaseId = releaseId == null || releaseId.isBlank() ? null : releaseId;
            resultConsumerId = resultConsumerId == null || resultConsumerId.isBlank() ? null : resultConsumerId;
        }
    }

    public record Result(String collectionId, boolean existing) {
    }
}
