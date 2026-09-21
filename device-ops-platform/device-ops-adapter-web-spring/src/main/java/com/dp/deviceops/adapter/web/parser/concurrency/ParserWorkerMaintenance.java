package com.dp.deviceops.adapter.web.parser.concurrency;

import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.WorkerCapabilityRepository;
import com.dp.deviceops.parser.runtime.port.ParserRuntimeTelemetryPort;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Scheduled coordination is deliberately separated from the worker lifecycle. */
public final class ParserWorkerMaintenance {

    private static final System.Logger LOG = System.getLogger(ParserWorkerMaintenance.class.getName());
    private final ParserWorkerCoordinator coordinator;
    private final ParseTaskRepository tasks;
    private final WorkerCapabilityRepository capabilities;
    private final ParserWorkerProperties properties;
    private final Clock clock;
    private final ParserRuntimeTelemetryPort telemetry;

    public ParserWorkerMaintenance(ParserWorkerCoordinator coordinator, ParseTaskRepository tasks,
            WorkerCapabilityRepository capabilities, ParserWorkerProperties properties, Clock clock) {
        this(coordinator, tasks, capabilities, properties, clock, ParserRuntimeTelemetryPort.NOOP);
    }

    public ParserWorkerMaintenance(ParserWorkerCoordinator coordinator, ParseTaskRepository tasks,
            WorkerCapabilityRepository capabilities, ParserWorkerProperties properties, Clock clock,
            ParserRuntimeTelemetryPort telemetry) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    @Scheduled(fixedDelayString = "${device-ops.parser.worker.poll-interval:1s}")
    public void poll() {
        runSafely("poll", coordinator::poll);
    }

    @Scheduled(fixedDelayString = "${device-ops.parser.worker.heartbeat-interval:10s}")
    public void heartbeat() {
        runSafely("heartbeat", () -> {
            Instant now = clock.instant();
            capabilities.heartbeat(coordinator.workerId(), coordinator.capabilities(), now.plus(properties.lease()));
            coordinator.renewActiveLeases(now.plus(properties.lease()));
        });
    }

    @Scheduled(fixedDelayString = "${device-ops.parser.worker.recovery-interval:5s}")
    public void recoverAndReconcile() {
        runSafely("recovery", () -> {
            Instant now = clock.instant();
            telemetry.leasesExpired(tasks.recoverExpired(now, properties.recoveryBatchSize()));
            capabilities.reconcileWaiting(now, properties.recoveryBatchSize());
        });
    }

    private static void runSafely(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "parser worker {0} failed: {1}",
                    operation, failure.getClass().getSimpleName());
        }
    }
}
