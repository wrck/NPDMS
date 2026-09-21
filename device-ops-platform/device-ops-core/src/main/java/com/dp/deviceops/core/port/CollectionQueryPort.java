package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CommandOutputBlock;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only collection evidence boundary. Implementations must enforce namespace and collection ownership in the
 * same lookup, plus project ownership when supplied, and must never return credentials or script content.
 */
public interface CollectionQueryPort {

    Optional<CollectionDetails> find(String namespace, String collectionId);

    Optional<CollectionDetails> find(String namespace, String projectKey, String collectionId);

    record CollectionDetails(String collectionId, String namespace, String projectKey, String externalRequestId,
                             String activityType, CollectionStatus status, ScriptIdentity script,
                             List<TargetDetails> targets) {
        public CollectionDetails {
            targets = List.copyOf(targets);
        }
    }

    record ScriptIdentity(String source, String key, String version, String sha256, String parserType) {
    }

    record TargetDetails(long targetId, ContextSnapshot contextSnapshot,
                         CollectionTarget.EndpointSnapshot endpointSnapshot, CollectionStatus status,
                         String stdout, String stderr, Integer exitCode, boolean truncated,
                         Map<String, String> parsedFacts, String outcome,
                         List<CommandOutputBlock> commandBlocks) {
        public TargetDetails {
            parsedFacts = Map.copyOf(parsedFacts);
            commandBlocks = List.copyOf(commandBlocks);
        }
    }

    record ContextSnapshot(ProjectSnapshot project, DeviceSnapshot device, Map<String, String> extensions) {
        public ContextSnapshot {
            extensions = Map.copyOf(extensions);
        }
    }

    record ProjectSnapshot(String namespace, String projectKey, String projectName, String projectCode) {
    }

    record DeviceSnapshot(String deviceKey, String deviceName, String vendor, String model) {
    }
}
