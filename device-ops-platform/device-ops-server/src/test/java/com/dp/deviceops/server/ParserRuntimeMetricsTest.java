package com.dp.deviceops.server;

import com.dp.deviceops.adapter.web.parser.concurrency.ParserWorkerCoordinator;
import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParserRuntimeObservationPort;
import com.dp.deviceops.parser.runtime.port.WorkerCapabilityRepository;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParserRuntimeMetricsTest {

    @Test
    void publishesStateTransitionsAndOnlyLowCardinalityTags() {
        var registry = new SimpleMeterRegistry();
        var state = new AtomicReference<>(snapshot(2, 0, 0, 7, 1));
        ParserRuntimeObservationPort observations = now -> state.get();
        ParserWorkerCoordinator coordinator = mock(ParserWorkerCoordinator.class);
        when(coordinator.availablePermits()).thenReturn(5);
        WorkerCapability capability = new WorkerCapability("1.0.0", null, null);
        WorkerCapabilityRepository workers = workers(capability);
        var metrics = new ParserRuntimeMetrics(registry, observations, workers, coordinator,
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

        assertGauge(registry, "device_ops_parser_task_queued", 2);
        assertGauge(registry, "device_ops_parser_task_oldest_queued_seconds", 7);
        assertGauge(registry, "device_ops_parser_worker_available_slots", 5);
        assertGauge(registry, "device_ops_parser_worker_capability_count", 1);

        state.set(snapshot(0, 1, 1, 0, 2));
        metrics.refresh();
        assertGauge(registry, "device_ops_parser_task_running", 1);
        assertGauge(registry, "device_ops_parser_task_waiting", 1);
        assertGauge(registry, "device_ops_parser_result_delivery_pending", 2);

        ParserCoordinate coordinate = new ParserCoordinate("synthetic-log", "1.2.3",
                "1.0.0", "rules-1", "projection-1", null, null);
        metrics.taskSucceeded(coordinate, Duration.ofMillis(40), 3);
        metrics.taskFailed(coordinate, "INVALID_INPUT", Duration.ofMillis(20));
        metrics.taskWaiting(coordinate, ParseWaitReason.PAYLOAD_UNAVAILABLE,
                "PARSER_PAYLOAD_UNAVAILABLE", Duration.ofMillis(10));
        metrics.releaseLoadFailed(coordinate, "IllegalStateException");
        metrics.releaseLoadFailed(coordinate, "raw-secret-device-output");
        metrics.leasesExpired(2);

        assertEquals(3, registry.get("device_ops_parser_task_duration_seconds")
                .tags("logType", "synthetic-log", "releaseVersion", "1.2.3").timer().count());
        assertEquals(1, registry.get("device_ops_parser_task_failed_total")
                .tags("logType", "synthetic-log", "releaseVersion", "1.2.3",
                        "errorCode", "INVALID_INPUT").counter().count());
        assertEquals(2, registry.get("device_ops_parser_lease_expired_total").counter().count());
        assertEquals(3, registry.get("device_ops_parser_result_unmapped_units")
                .tags("logType", "synthetic-log", "releaseVersion", "1.2.3").counter().count());
        assertFalse(registry.getMeters().toString().contains("raw-secret-device-output"));
    }

    private static ParserRuntimeObservationPort.RuntimeSnapshot snapshot(
            long queued, long running, long waiting, long oldest, long pending) {
        return new ParserRuntimeObservationPort.RuntimeSnapshot(
                Map.of(ParseTaskState.QUEUED, queued, ParseTaskState.RUNNING, running,
                        ParseTaskState.WAITING, waiting),
                Map.of(ParseWaitReason.PAYLOAD_UNAVAILABLE, waiting), oldest, pending);
    }

    private static WorkerCapabilityRepository workers(WorkerCapability capability) {
        return new WorkerCapabilityRepository() {
            @Override public void heartbeat(String workerId, Set<WorkerCapability> capabilities, Instant expiresAt) { }
            @Override public long countAvailable(ParserCoordinate coordinate, Instant now) { return 1; }
            @Override public List<WorkerHeartbeat> listActive(Instant now) {
                return List.of(new WorkerHeartbeat("worker-a", Set.of(capability), now, now.plusSeconds(30)));
            }
            @Override public int reconcileWaiting(Instant now, int limit) { return 0; }
        };
    }

    private static void assertGauge(SimpleMeterRegistry registry, String name, double expected) {
        assertEquals(expected, registry.get(name).gauge().value());
    }
}
