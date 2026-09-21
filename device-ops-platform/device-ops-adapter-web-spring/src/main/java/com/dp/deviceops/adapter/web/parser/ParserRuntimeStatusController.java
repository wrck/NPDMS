package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.adapter.web.parser.concurrency.ParserWorkerCoordinator;
import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParserRuntimeObservationPort;
import com.dp.deviceops.parser.runtime.port.WorkerCapabilityRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/parser-runtime")
public class ParserRuntimeStatusController {

    private final ParserWorkerCoordinator coordinator;
    private final ParserRuntimeObservationPort observations;
    private final WorkerCapabilityRepository workers;
    private final Clock clock;

    public ParserRuntimeStatusController(ParserWorkerCoordinator coordinator,
            ParserRuntimeObservationPort observations, WorkerCapabilityRepository workers, Clock clock) {
        this.coordinator = coordinator;
        this.observations = observations;
        this.workers = workers;
        this.clock = clock;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('SCOPE_parser:release:read')")
    public RuntimeStatus status() {
        Instant now = clock.instant();
        var snapshot = observations.snapshot(now);
        var heartbeat = workers.listActive(now).stream()
                .filter(item -> item.workerId().equals(coordinator.workerId())).findFirst().orElse(null);
        List<WorkerCapability> supported = coordinator.capabilities().stream()
                .sorted(Comparator.comparing(WorkerCapability::engineVersion)
                        .thenComparing(value -> value.extensionId() == null ? "" : value.extensionId())
                        .thenComparing(value -> value.extensionVersion() == null ? "" : value.extensionVersion()))
                .toList();
        return new RuntimeStatus(coordinator.workerId(), supported, coordinator.activeCount(),
                coordinator.queuedCount(), coordinator.availablePermits(),
                heartbeat == null ? null : heartbeat.lastHeartbeatAt(), snapshot.waitingCounts());
    }

    public record RuntimeStatus(String workerId, List<WorkerCapability> supportedCoordinates,
            int executorActiveCount, int executorQueuedCount, int executorAvailableSlots,
            Instant latestHeartbeatAt, Map<ParseWaitReason, Long> waitingCounts) {
        public RuntimeStatus {
            supportedCoordinates = List.copyOf(supportedCoordinates);
            waitingCounts = Map.copyOf(waitingCounts);
        }
    }
}
