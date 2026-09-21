package com.dp.deviceops.core.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dedicated authorized task evidence, independent of the management script catalog. */
public interface CollectionEvidenceQueryPort {
    Optional<Evidence> find(ManagementQueryPort.Scope scope, String namespace, String projectKey, String collectionId);
    record Evidence(Metadata metadata, Input input, Submission submission, ExecutionFacts executionFacts) {}
    record Metadata(String collectionId, String namespace, String projectKey, String externalRequestId,
                    String activityType, Instant createdAt) {}
    record Input(String source, String key, String version, String policy, String parserType, String sha256,
                 String contentStatus, String content) {}
    record Submission(String provenance, Map<String, Object> snapshot, List<String> omittedFields) {}
    record ExecutionFacts(List<Target> targets, SemanticParsing semanticParsing) {}
    record Target(long targetId, String deviceKey, String protocol, String host, int port, String username,
                  String hostKeyFingerprint, String status) {}
    record SemanticParsing(String logType, String releaseId, String inputFormat, String resultConsumerId) {}
}
