package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CollectionTask;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.port.CollectionRepository;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Submits collection tasks using caller-supplied execution snapshots.
 *
 * <p>Task 6 persistence must atomically enforce a database unique constraint over namespace and idempotency key,
 * catch a duplicate insert, and return the row that won through
 * {@link CollectionRepository#saveOrGetExisting(com.dp.deviceops.core.model.CollectionTask)}. This service does not
 * emulate a cross-process concurrency boundary.</p>
 */
public final class SubmitCollectionService {

    private final CollectionRepository repository;
    private final Supplier<String> collectionIdSupplier;
    public SubmitCollectionService(CollectionRepository repository, Supplier<String> collectionIdSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.collectionIdSupplier = Objects.requireNonNull(collectionIdSupplier, "collectionIdSupplier must not be null");
    }

    public SubmitCollectionResult submit(SubmitCollectionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validate(command);

        return findExisting(command.namespace(), command.idempotencyKey(), command.submissionFingerprint())
                .map(existing -> {
                    validateProject(existing, command);
                    return new SubmitCollectionResult(existing.taskId(), true);
                })
                .orElseGet(() -> saveNew(command));
    }

    public java.util.Optional<String> findExistingId(String namespace, String idempotencyKey) {
        return findExistingId(namespace, idempotencyKey, null);
    }

    /** The supplied fingerprint includes exact project scope and the external caller's identity. */
    public java.util.Optional<String> findExistingId(String namespace, String idempotencyKey,
                                                    String submissionFingerprint) {
        return findExisting(namespace, idempotencyKey, submissionFingerprint).map(CollectionTask::taskId);
    }

    private java.util.Optional<CollectionTask> findExisting(String namespace, String idempotencyKey,
                                                           String submissionFingerprint) {
        return repository.findByIdempotencyKey(requireText(namespace, "namespace"),
                        requireText(idempotencyKey, "idempotencyKey"))
                .filter(existing -> namespace.equals(existing.namespace())
                        && idempotencyKey.equals(existing.idempotencyKey()))
                .map(existing -> {
                    validateFingerprint(existing, submissionFingerprint);
                    return existing;
                });
    }

    private static void validateFingerprint(CollectionTask existing, String fingerprint) {
        if (fingerprint != null && !fingerprint.equals(existing.submissionFingerprint())) {
            throw new CollectionIdempotencyConflictException();
        }
    }

    private static void validateProject(CollectionTask existing, SubmitCollectionCommand command) {
        if (command.submissionFingerprint() != null
                && !Objects.equals(existing.projectKey().orElse(null), command.projectKey())) {
            throw new CollectionIdempotencyConflictException();
        }
    }

    private SubmitCollectionResult saveNew(SubmitCollectionCommand command) {
        CollectionTask submitted = CollectionTask.submitted(requireText(collectionIdSupplier.get(), "collectionId"),
                command.namespace(), command.projectKey(), command.externalRequestId(), command.idempotencyKey(),
                command.targets(), command.script(), command.activityType(), command.callbackUri(),
                command.semanticParsing(), command.submissionFingerprint()).withSubmissionSnapshot(command.submissionSnapshot());
        CollectionRepository.SaveResult saved = Objects.requireNonNull(repository.saveOrGetExisting(submitted),
                "repository must return save result");
        if (!command.namespace().equals(saved.task().namespace())
                || command.submissionFingerprint() != null
                && !command.idempotencyKey().equals(saved.task().idempotencyKey())) {
            throw new CollectionIdempotencyConflictException();
        }
        validateFingerprint(saved.task(), command.submissionFingerprint());
        validateProject(saved.task(), command);
        return new SubmitCollectionResult(saved.task().taskId(), !saved.created());
    }

    private static void validate(SubmitCollectionCommand command) {
        requireText(command.namespace(), "namespace");
        requireText(command.idempotencyKey(), "idempotencyKey");
        if (command.targets().isEmpty()) {
            throw new IllegalArgumentException("targets must not be empty");
        }
        Objects.requireNonNull(command.script(), "script must not be null");
        for (CollectionTarget target : command.targets()) {
            CollectionTarget requiredTarget = Objects.requireNonNull(target, "target must not be null");
            if (command.projectKey() != null && requiredTarget.contextSnapshot().project()
                    .filter(project -> command.namespace().equals(project.namespace())
                            && command.projectKey().equals(project.projectKey()))
                    .isEmpty()) {
                throw new IllegalArgumentException("target snapshot must belong to the submitted namespace and project");
            }
            if (command.callbackUri() != null
                    && (requiredTarget.contextSnapshot().project().isEmpty()
                    || requiredTarget.contextSnapshot().device().isEmpty())) {
                throw new IllegalArgumentException("callback requires project and device context for every target");
            }
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public record SubmitCollectionCommand(String namespace, String projectKey, String externalRequestId,
                                          String idempotencyKey, List<CollectionTarget> targets, ScriptArtifact script,
                                          String activityType, URI callbackUri,
                                          com.dp.deviceops.core.model.CollectionSemanticParsing semanticParsing,
                                          String submissionFingerprint, String submissionSnapshot) {

        public SubmitCollectionCommand(String namespace, String projectKey, String externalRequestId,
                String idempotencyKey, List<CollectionTarget> targets, ScriptArtifact script,
                String activityType, URI callbackUri,
                com.dp.deviceops.core.model.CollectionSemanticParsing semanticParsing, String submissionFingerprint) {
            this(namespace, projectKey, externalRequestId, idempotencyKey, targets, script,
                    activityType, callbackUri, semanticParsing, submissionFingerprint, null);
        }

        public SubmitCollectionCommand(String namespace, String projectKey, String externalRequestId,
                String idempotencyKey, List<CollectionTarget> targets, ScriptArtifact script,
                String activityType, URI callbackUri,
                com.dp.deviceops.core.model.CollectionSemanticParsing semanticParsing) {
            this(namespace, projectKey, externalRequestId, idempotencyKey, targets, script,
                    activityType, callbackUri, semanticParsing, null);
        }

        public SubmitCollectionCommand(String namespace, String projectKey, String externalRequestId,
                String idempotencyKey, List<CollectionTarget> targets, ScriptArtifact script,
                String activityType, URI callbackUri) {
            this(namespace, projectKey, externalRequestId, idempotencyKey, targets, script,
                    activityType, callbackUri, null);
        }

        public SubmitCollectionCommand {
            projectKey = optionalText(projectKey);
            targets = List.copyOf(Objects.requireNonNull(targets, "targets must not be null"));
        }
    }

    public record SubmitCollectionResult(String collectionId, boolean existing) {
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
