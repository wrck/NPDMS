package com.dp.deviceops.core.model;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A collection request that owns its execution targets without owning their master data.
 */
public final class CollectionTask {

    private final String taskId;
    private final String namespace;
    private final String projectKey;
    private final String externalRequestId;
    private final String idempotencyKey;
    private final List<CollectionTarget> targets;
    private final ScriptArtifact script;
    private final String activityType;
    private final URI callbackUri;
    private final CollectionSemanticParsing semanticParsing;
    private final String submissionFingerprint;
    private final String submissionSnapshot;

    private CollectionTask(CollectionTask original, String snapshot) {
        this.taskId = original.taskId; this.namespace = original.namespace; this.projectKey = original.projectKey;
        this.externalRequestId = original.externalRequestId; this.idempotencyKey = original.idempotencyKey;
        this.targets = original.targets; this.script = original.script; this.activityType = original.activityType;
        this.callbackUri = original.callbackUri; this.semanticParsing = original.semanticParsing;
        this.submissionFingerprint = original.submissionFingerprint; this.submissionSnapshot = snapshot;
    }
    public CollectionTask withSubmissionSnapshot(String snapshot) { return new CollectionTask(this, snapshot); }
    public String submissionSnapshot() { return submissionSnapshot; }

    private CollectionTask(String taskId, String namespace, String projectKey, String externalRequestId,
                           String idempotencyKey, List<CollectionTarget> targets, ScriptArtifact script,
                           String activityType, URI callbackUri, CollectionSemanticParsing semanticParsing,
                           String submissionFingerprint) {
        this.taskId = requireText(taskId, "taskId");
        this.namespace = requireText(namespace, "namespace");
        this.projectKey = optionalText(projectKey);
        this.externalRequestId = externalRequestId;
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.targets = requireTargets(targets);
        this.script = Objects.requireNonNull(script, "script must not be null");
        this.activityType = activityType;
        this.callbackUri = callbackUri;
        this.semanticParsing = semanticParsing;
        this.submissionFingerprint = submissionFingerprint;
        this.submissionSnapshot = null;
    }

    public static CollectionTask submitted(String taskId, String namespace, String projectKey, String externalRequestId,
                                           String idempotencyKey, List<CollectionTarget> targets, ScriptArtifact script,
                                           String activityType, URI callbackUri) {
        return new CollectionTask(taskId, namespace, projectKey, externalRequestId, idempotencyKey, targets, script,
                activityType, callbackUri, null, null);
    }

    public static CollectionTask submitted(String taskId, String namespace, String projectKey, String externalRequestId,
                                           String idempotencyKey, List<CollectionTarget> targets, ScriptArtifact script,
                                           String activityType, URI callbackUri,
                                           CollectionSemanticParsing semanticParsing) {
        return submitted(taskId, namespace, projectKey, externalRequestId, idempotencyKey, targets, script,
                activityType, callbackUri, semanticParsing, null);
    }

    public static CollectionTask submitted(String taskId, String namespace, String projectKey, String externalRequestId,
                                           String idempotencyKey, List<CollectionTarget> targets, ScriptArtifact script,
                                           String activityType, URI callbackUri,
                                           CollectionSemanticParsing semanticParsing, String submissionFingerprint) {
        return new CollectionTask(taskId, namespace, projectKey, externalRequestId, idempotencyKey, targets, script,
                activityType, callbackUri, semanticParsing, submissionFingerprint);
    }

    public String submissionFingerprint() {
        return submissionFingerprint;
    }

    public String taskId() {
        return taskId;
    }

    public String namespace() {
        return namespace;
    }

    public Optional<String> projectKey() {
        return Optional.ofNullable(projectKey);
    }

    public String externalRequestId() {
        return externalRequestId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public List<CollectionTarget> targets() {
        return targets;
    }

    public ScriptArtifact script() {
        return script;
    }

    public String activityType() {
        return activityType;
    }

    public URI callbackUri() {
        return callbackUri;
    }

    public Optional<CollectionSemanticParsing> semanticParsing() {
        return Optional.ofNullable(semanticParsing);
    }

    private static List<CollectionTarget> requireTargets(List<CollectionTarget> targets) {
        Objects.requireNonNull(targets, "targets must not be null");
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("targets must not be empty");
        }
        return List.copyOf(targets);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
