package com.dp.deviceops.server;

import com.dp.deviceops.adapter.web.parser.concurrency.ParserWorkerCoordinator;
import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.port.ParserRuntimeObservationPort;
import com.dp.deviceops.parser.runtime.port.ParserRuntimeTelemetryPort;
import com.dp.deviceops.parser.runtime.port.WorkerCapabilityRepository;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/** Publishes parser runtime metrics without task, device or payload cardinality. */
public final class ParserRuntimeMetrics implements ParserRuntimeTelemetryPort {

    private final MeterRegistry registry;
    private final ParserRuntimeObservationPort observations;
    private final WorkerCapabilityRepository workers;
    private final IntSupplier availableSlots;
    private final Clock clock;
    private final Map<ParseTaskState, AtomicLong> taskCounts = new EnumMap<>(ParseTaskState.class);
    private final Map<ParseWaitReason, AtomicLong> waitingCounts = new EnumMap<>(ParseWaitReason.class);
    private final AtomicLong oldestQueuedSeconds = new AtomicLong();
    private final AtomicLong pendingDeliveries = new AtomicLong();
    private final AtomicLong activeCapabilityCount = new AtomicLong();
    private final Counter expiredLeases;

    public ParserRuntimeMetrics(MeterRegistry registry, ParserRuntimeObservationPort observations,
            WorkerCapabilityRepository workers, ParserWorkerCoordinator coordinator, Clock clock) {
        this(registry, observations, workers, coordinator::availablePermits, clock);
    }

    public ParserRuntimeMetrics(MeterRegistry registry, ParserRuntimeObservationPort observations,
            WorkerCapabilityRepository workers, IntSupplier availableSlots, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.observations = Objects.requireNonNull(observations, "observations");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.availableSlots = Objects.requireNonNull(availableSlots, "availableSlots");
        this.clock = Objects.requireNonNull(clock, "clock");

        for (ParseTaskState state : ParseTaskState.values()) taskCounts.put(state, new AtomicLong());
        for (ParseWaitReason reason : ParseWaitReason.values()) waitingCounts.put(reason, new AtomicLong());
        gauge("device_ops_parser_task_queued", taskCounts.get(ParseTaskState.QUEUED));
        gauge("device_ops_parser_task_running", taskCounts.get(ParseTaskState.RUNNING));
        gauge("device_ops_parser_task_waiting", taskCounts.get(ParseTaskState.WAITING));
        gauge("device_ops_parser_task_oldest_queued_seconds", oldestQueuedSeconds);
        gauge("device_ops_parser_result_delivery_pending", pendingDeliveries);
        gauge("device_ops_parser_worker_capability_count", activeCapabilityCount);
        Gauge.builder("device_ops_parser_worker_available_slots", availableSlots,
                        IntSupplier::getAsInt)
                .register(registry);
        expiredLeases = Counter.builder("device_ops_parser_lease_expired_total").register(registry);
        refresh();
    }

    @Scheduled(fixedDelayString = "${device-ops.parser.metrics.refresh-interval:5s}")
    public void refresh() {
        var snapshot = observations.snapshot(clock.instant());
        taskCounts.forEach((state, value) -> value.set(snapshot.taskCounts().getOrDefault(state, 0L)));
        waitingCounts.forEach((reason, value) -> value.set(snapshot.waitingCounts().getOrDefault(reason, 0L)));
        oldestQueuedSeconds.set(snapshot.oldestQueuedSeconds());
        pendingDeliveries.set(snapshot.pendingResultDeliveries());
        activeCapabilityCount.set(workers.listActive(clock.instant()).stream()
                .mapToLong(worker -> worker.capabilities().size()).sum());
    }

    @Override
    public void taskSucceeded(ParserCoordinate coordinate, Duration duration, long unmappedUnits) {
        duration(coordinate).record(duration);
        if (unmappedUnits > 0) {
            Counter.builder("device_ops_parser_result_unmapped_units")
                    .tags(coordinateTags(coordinate)).register(registry).increment(unmappedUnits);
        }
    }

    @Override
    public void taskFailed(ParserCoordinate coordinate, String errorCode, Duration duration) {
        duration(coordinate).record(duration);
        Counter.builder("device_ops_parser_task_failed_total").tags(coordinateTags(coordinate))
                .tag("errorCode", safeErrorCode(errorCode)).register(registry).increment();
    }

    @Override
    public void taskWaiting(ParserCoordinate coordinate, ParseWaitReason reason,
            String errorCode, Duration duration) {
        duration(coordinate).record(duration);
    }

    @Override
    public void leasesExpired(int count) {
        if (count > 0) expiredLeases.increment(count);
    }

    @Override
    public void releaseLoadFailed(ParserCoordinate coordinate, String reason) {
        Counter.builder("device_ops_parser_release_load_failure_total").tags(coordinateTags(coordinate))
                .tag("reason", safeReason(reason)).register(registry).increment();
    }

    private Timer duration(ParserCoordinate coordinate) {
        return Timer.builder("device_ops_parser_task_duration_seconds")
                .tags(coordinateTags(coordinate)).register(registry);
    }

    private String[] coordinateTags(ParserCoordinate coordinate) {
        return new String[]{"logType", coordinate.logType(), "releaseVersion", coordinate.releaseVersion()};
    }

    private void gauge(String name, AtomicLong value) {
        Gauge.builder(name, value, AtomicLong::get).register(registry);
    }

    private static String safeErrorCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}") ? value : "UNKNOWN";
    }

    private static String safeReason(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9.]{0,79}") ? value : "UNKNOWN";
    }
}
