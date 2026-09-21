package com.dp.deviceops.parser.runtime.port;

import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.semantic.ParserCoordinate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface WorkerCapabilityRepository {
    void heartbeat(String workerId, Set<WorkerCapability> capabilities, Instant expiresAt);

    long countAvailable(ParserCoordinate coordinate, Instant now);

    List<WorkerHeartbeat> listActive(Instant now);

    int reconcileWaiting(Instant now, int limit);

    record WorkerHeartbeat(
            String workerId,
            Set<WorkerCapability> capabilities,
            Instant lastHeartbeatAt,
            Instant heartbeatExpiresAt) {
        public WorkerHeartbeat {
            capabilities = Set.copyOf(capabilities);
        }
    }
}
