package com.dp.deviceops.adapter.web.concurrency;

import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.service.CollectionWorker;
import com.dp.deviceops.core.service.ExecutionLeaseLostException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Performs bounded, FIFO connection admission before work reaches the global execution pool.
 * A waiting connection therefore consumes instance capacity but never consumes a global worker.
 */
public final class KeyedCollectionDispatcher implements CollectionMaintenanceScheduler, AutoCloseable {

    private static final System.Logger LOG = System.getLogger(KeyedCollectionDispatcher.class.getName());
    public static final String CONNECTION_BUSY_TIMEOUT = "CONNECTION_BUSY_TIMEOUT";
    public static final String SHUTDOWN_CANCELLED = "SHUTDOWN_CANCELLED";

    private final Object monitor = new Object();
    private final Map<ConnectionKey, KeyState> keys = new HashMap<>();
    private final Set<PendingWork> outstandingWork = new LinkedHashSet<>();
    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor waitTimeouts;
    private final CollectionWorker worker;
    private final CollectionExecutionPersistencePort persistence;
    private final Clock clock;
    private final Duration connectionWaitTimeout;
    private final Duration leaseHeartbeatInterval;
    private final Duration recoveryHandoffLease;
    private final int perConnectionLimit;
    private final int capacity;
    private final AtomicLong rejectedCount;
    private final AtomicLong timeoutCount = new AtomicLong();
    private boolean accepting = true;
    private LifecycleState lifecycleState = LifecycleState.RUNNING;
    private ShutdownResult shutdownResult;
    private int waitingCount;
    private int recoverableHandoffCount;
    private int failedHandoffCount;
    private int unhandledReturnedTaskCount;

    public KeyedCollectionDispatcher(
            ThreadPoolExecutor executor,
            CollectionWorker worker,
            CollectionExecutionPersistencePort persistence,
            Clock clock,
            Duration connectionWaitTimeout,
            Duration leaseHeartbeatInterval,
            Duration recoveryHandoffLease,
            int perConnectionLimit,
            int capacity,
            AtomicLong rejectedCount) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.connectionWaitTimeout = requirePositive(connectionWaitTimeout, "connectionWaitTimeout");
        this.leaseHeartbeatInterval = requirePositive(leaseHeartbeatInterval, "leaseHeartbeatInterval");
        this.recoveryHandoffLease = requirePositive(recoveryHandoffLease, "recoveryHandoffLease");
        if (perConnectionLimit < 1) {
            throw new IllegalArgumentException("perConnectionLimit must be positive");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.perConnectionLimit = perConnectionLimit;
        this.capacity = capacity;
        this.rejectedCount = Objects.requireNonNull(rejectedCount, "rejectedCount");
        waitTimeouts = new ScheduledThreadPoolExecutor(
                2, Thread.ofVirtual().name("device-ops-connection-maintenance-", 0).factory());
        waitTimeouts.setRemoveOnCancelPolicy(true);
        waitTimeouts.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        waitTimeouts.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        waitTimeouts.scheduleWithFixedDelay(
                this::renewPendingLeases,
                leaseHeartbeatInterval.toNanos(),
                leaseHeartbeatInterval.toNanos(),
                TimeUnit.NANOSECONDS);
    }

    /** Claims and admits one work item without blocking the caller for a connection slot. */
    public void submit(CollectionWorker.WorkItem item) {
        Objects.requireNonNull(item, "item");
        PendingWork pending = new PendingWork(item, ConnectionKey.from(item.connectionSpec()));
        synchronized (monitor) {
            if (!accepting || outstandingWork.size() >= capacity) {
                rejectedCount.incrementAndGet();
                throw new RejectedExecutionException("collection dispatcher capacity is exhausted");
            }
            outstandingWork.add(pending);
        }

        boolean claimed;
        Instant claimedAt = clock.instant();
        try {
            claimed = persistence.claimPending(
                    item.targetId(),
                    item.workerId(),
                    claimedAt,
                    recoveryHandoffLease);
        } catch (RuntimeException failure) {
            abandonUnclaimed(pending);
            throw failure;
        }
        if (!claimed) {
            abandonUnclaimed(pending);
            return;
        }

        List<PendingWork> ready;
        boolean cancelledDuringClaim;
        synchronized (monitor) {
            cancelledDuringClaim = pending.cancelRequested || lifecycleState == LifecycleState.FORCED;
            if (cancelledDuringClaim) {
                beginFinalizationLocked(pending);
                ready = List.of();
            } else {
                KeyState state = keys.computeIfAbsent(pending.key, ignored -> new KeyState());
                state.waiting.addLast(pending);
                pending.state = WorkState.WAITING;
                waitingCount++;
                if (lifecycleState == LifecycleState.RUNNING) {
                    pending.timeout = waitTimeouts.schedule(
                            () -> expireWaiting(pending),
                            connectionWaitTimeout.toNanos(),
                            TimeUnit.NANOSECONDS);
                }
                ready = drainReadyLocked(pending.key, state);
            }
        }
        if (cancelledDuringClaim) {
            preserveRecoveryBaseline(pending);
            closeContext(pending);
            finishFinalization(pending);
            return;
        }
        dispatch(ready, pending);
    }

    public int waitingCount() {
        synchronized (monitor) {
            return waitingCount;
        }
    }

    /** Request a stop; running work is terminalized only after the execution thread exits. */
    public boolean cancel(long targetId) {
        PendingWork selected;
        boolean finalizeHere = false;
        List<PendingWork> ready = List.of();
        synchronized (monitor) {
            selected = outstandingWork.stream().filter(work -> work.item.targetId() == targetId)
                    .findFirst().orElse(null);
            if (selected == null || selected.state == WorkState.CLAIMING) return false;
            if (selected.state == WorkState.FINISHED) return true;
            selected.cancelRequested = true;
            selected.cancellationReason = "CALLER_CANCELLED";
            if (selected.state == WorkState.WAITING) {
                removeWaitingLocked(selected);
                beginFinalizationLocked(selected);
                finalizeHere = true;
            } else if (selected.state == WorkState.DISPATCHED) {
                selected.future.cancel(false);
                executor.remove(selected.future);
                releaseConnectionSlotLocked(selected);
                beginFinalizationLocked(selected);
                finalizeHere = true;
                KeyState state = keys.get(selected.key);
                if (state != null) ready = drainReadyLocked(selected.key, state);
            } else if (selected.state == WorkState.RUNNING) {
                // The same worker also commits output to JDBC/H2. Interrupting it can close the database file.
                selected.item.context().requestCancellation();
            }
        }
        if (finalizeHere) {
            if (!cancelPersisted(selected, selected.cancellationReason)) preserveRecoveryBaseline(selected);
            closeContext(selected);
            finishFinalization(selected);
        }
        dispatch(ready, null);
        return true;
    }

    public int activeKeyCount() {
        synchronized (monitor) {
            return keys.size();
        }
    }

    public int outstandingCount() {
        synchronized (monitor) {
            return outstandingWork.size();
        }
    }

    public int remainingCapacity() {
        return capacity - outstandingCount();
    }

    public long timeoutCount() {
        return timeoutCount.get();
    }

    public boolean isAccepting() {
        synchronized (monitor) {
            return accepting;
        }
    }

    /**
     * Registers collection maintenance on the same executor and shutdown deadline
     * as lease heartbeats. Registration is rejected once shutdown has started.
     */
    @Override
    public boolean scheduleWithFixedDelay(Duration interval, Runnable maintenance) {
        requirePositive(interval, "interval");
        Objects.requireNonNull(maintenance, "maintenance");
        synchronized (monitor) {
            if (lifecycleState != LifecycleState.RUNNING) {
                return false;
            }
            waitTimeouts.scheduleWithFixedDelay(
                    () -> runMaintenance(maintenance),
                    0,
                    interval.toNanos(),
                    TimeUnit.NANOSECONDS);
            return true;
        }
    }

    private void runMaintenance(Runnable maintenance) {
        try {
            maintenance.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING,
                    "collection maintenance failed: {0}",
                    failure.getClass().getSimpleName());
        }
    }

    /**
     * Applies the JDK two-phase shutdown pattern to both dispatcher-owned executors.
     * The forced-phase callback is invoked after {@code shutdownNow()} and before the
     * second termination wait, so protocol adapters can actively unblock I/O.
     */
    public ShutdownResult shutdown(
            Duration totalAwait,
            Duration gracefulSlice,
            Consumer<ShutdownResult> forcedPhase) {
        Objects.requireNonNull(totalAwait, "totalAwait");
        Objects.requireNonNull(gracefulSlice, "gracefulSlice");
        Objects.requireNonNull(forcedPhase, "forcedPhase");
        if (totalAwait.isNegative() || gracefulSlice.isNegative()) {
            throw new IllegalArgumentException("shutdown durations must not be negative");
        }
        if (gracefulSlice.compareTo(totalAwait) > 0) {
            throw new IllegalArgumentException("gracefulSlice must not exceed totalAwait");
        }

        long started = System.nanoTime();
        long deadline = started + totalAwait.toNanos();
        long gracefulDeadline = started + gracefulSlice.toNanos();
        synchronized (monitor) {
            if (lifecycleState != LifecycleState.RUNNING) {
                return shutdownResult == null ? currentShutdownResult() : shutdownResult;
            }
            accepting = false;
            lifecycleState = LifecycleState.GRACEFUL;
        }

        // No periodic renewal or timeout work may be scheduled after shutdown starts.
        waitTimeouts.shutdown();
        WaitOutcome drained = awaitOutstanding(gracefulDeadline);
        boolean interrupted = drained.interrupted();
        if (drained.completed()) {
            executor.shutdown();
        }
        WaitOutcome gracefulWorkers = drained.completed() && !interrupted
                ? awaitTermination(executor, gracefulDeadline)
                : new WaitOutcome(executor.isTerminated(), false);
        interrupted |= gracefulWorkers.interrupted();
        WaitOutcome gracefulMaintenance = !interrupted
                ? awaitTermination(waitTimeouts, gracefulDeadline)
                : new WaitOutcome(waitTimeouts.isTerminated(), false);
        interrupted |= gracefulMaintenance.interrupted();

        if (!interrupted && drained.completed()
                && gracefulWorkers.completed() && gracefulMaintenance.completed()) {
            ShutdownResult result = finishShutdown(
                    ShutdownMode.ORDERLY, true, true, false);
            return result;
        }

        List<PendingWork> neverStarted = beginForcedShutdown();
        waitTimeouts.shutdownNow();
        List<Runnable> returned = executor.shutdownNow();

        ShutdownResult forcedSnapshot = currentShutdownResult(ShutdownMode.FORCED, interrupted);
        try {
            forcedPhase.accept(forcedSnapshot);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.ERROR,
                    "forced shutdown cancellation aid failed: {0}",
                    failure.getClass().getSimpleName());
        }
        neverStarted.addAll(terminalizeReturnedTasks(returned));
        neverStarted.forEach(this::finalizeNeverStarted);

        // InterruptedException clears the caller's interrupt status. Keep using the
        // shared deadline for the required post-shutdownNow waits, then restore the
        // status exactly once before returning.
        WaitOutcome forcedWorkers = awaitTermination(executor, deadline);
        interrupted |= forcedWorkers.interrupted();
        WaitOutcome forcedMaintenance = awaitTermination(waitTimeouts, deadline);
        interrupted |= forcedMaintenance.interrupted();

        ShutdownResult result = finishShutdown(
                ShutdownMode.FORCED,
                forcedWorkers.completed(),
                forcedMaintenance.completed(),
                interrupted);
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    public ShutdownResult shutdown(Duration totalAwait, Duration gracefulSlice) {
        return shutdown(totalAwait, gracefulSlice, ignored -> { });
    }

    public ShutdownResult shutdown(Duration totalAwait) {
        Objects.requireNonNull(totalAwait, "totalAwait");
        return shutdown(totalAwait, totalAwait.dividedBy(2));
    }

    @Override
    public void close() {
        shutdown(Duration.ZERO, Duration.ZERO);
    }

    private void renewPendingLeases() {
        List<PendingWork> snapshot;
        synchronized (monitor) {
            snapshot = outstandingWork.stream()
                    .filter(pending -> pending.state == WorkState.WAITING
                            || pending.state == WorkState.DISPATCHED
                            || pending.state == WorkState.RUNNING)
                    .toList();
        }
        if (snapshot.isEmpty()) {
            return;
        }
        try {
            Set<Long> leaseLost = Set.copyOf(persistence.renewPendingLeases(
                    snapshot.stream()
                            .map(pending -> new CollectionExecutionPersistencePort.ClaimedTarget(
                                    pending.item.targetId(), pending.item.workerId()))
                            .toList(),
                    recoveryHandoffLease));
            snapshot.stream()
                    .filter(pending -> leaseLost.contains(pending.item.targetId()))
                    .forEach(this::abandonLeaseLostPending);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING,
                    "collection lease heartbeat batch failed: {0}",
                    failure.getClass().getSimpleName());
        }
    }

    private void abandonLeaseLostPending(PendingWork pending) {
        boolean close = false;
        boolean interrupt = false;
        synchronized (monitor) {
            if (pending.state == WorkState.WAITING) {
                removeWaitingLocked(pending);
                beginFinalizationLocked(pending);
                close = true;
            } else if (pending.state == WorkState.DISPATCHED && pending.future.cancel(false)) {
                executor.remove(pending.future);
                releaseConnectionSlotLocked(pending);
                beginFinalizationLocked(pending);
                close = true;
            } else if (pending.state == WorkState.RUNNING) {
                pending.cancelRequested = true;
                interrupt = true;
            }
        }
        if (interrupt) {
            pending.future.cancel(true);
        }
        if (close) {
            closeContext(pending);
            finishFinalization(pending);
        }
    }

    private void abandonUnclaimed(PendingWork pending) {
        synchronized (monitor) {
            beginFinalizationLocked(pending);
        }
        closeContext(pending);
        finishFinalization(pending);
    }

    private void expireWaiting(PendingWork pending) {
        synchronized (monitor) {
            if (pending.state != WorkState.WAITING) {
                return;
            }
            removeWaitingLocked(pending);
            beginFinalizationLocked(pending);
            timeoutCount.incrementAndGet();
        }
        if (!failPersisted(pending, CONNECTION_BUSY_TIMEOUT)) {
            preserveRecoveryBaseline(pending);
        }
        closeContext(pending);
        finishFinalization(pending);
    }

    private List<PendingWork> drainReadyLocked(ConnectionKey key, KeyState state) {
        List<PendingWork> ready = new ArrayList<>();
        while (lifecycleState != LifecycleState.FORCED
                && lifecycleState != LifecycleState.TERMINATED
                && state.active < perConnectionLimit
                && !state.waiting.isEmpty()) {
            PendingWork pending = state.waiting.removeFirst();
            waitingCount--;
            state.active++;
            pending.state = WorkState.DISPATCHED;
            if (pending.timeout != null) {
                pending.timeout.cancel(false);
            }
            pending.future = new PendingTask(pending, () -> {
                run(pending);
                return null;
            });
            ready.add(pending);
        }
        cleanupKeyLocked(key, state);
        return ready;
    }

    private void dispatch(List<PendingWork> ready, PendingWork submitted) {
        for (PendingWork pending : ready) {
            RejectedExecutionException rejection = null;
            boolean shutdownHandoff = false;
            synchronized (monitor) {
                if (pending.state != WorkState.DISPATCHED) {
                    continue;
                }
                if (pending.cancelRequested
                        || lifecycleState == LifecycleState.FORCED
                        || lifecycleState == LifecycleState.TERMINATED) {
                    pending.future.cancel(false);
                    releaseConnectionSlotLocked(pending);
                    beginFinalizationLocked(pending);
                    shutdownHandoff = true;
                } else {
                    try {
                        // Holding the dispatcher monitor makes execute() and forced
                        // queue capture one atomic admission decision. A started task
                        // can only enter run() after this monitor is released.
                        executor.execute(pending.future);
                        pending.submittedToExecutor = true;
                    } catch (RejectedExecutionException rejected) {
                        releaseConnectionSlotLocked(pending);
                        beginFinalizationLocked(pending);
                        rejection = rejected;
                    }
                }
            }
            if (shutdownHandoff || rejection != null) {
                if (shutdownHandoff) {
                    preserveRecoveryBaseline(pending);
                } else if (!failPersisted(pending, "QUEUE_FULL")) {
                    preserveRecoveryBaseline(pending);
                }
                closeContext(pending);
                finishFinalization(pending);
                if (rejection != null && pending == submitted) {
                    throw rejection;
                }
            }
        }
    }

    private void run(PendingWork pending) {
        boolean cancelledBeforeStart;
        synchronized (monitor) {
            if (pending.state != WorkState.DISPATCHED) {
                return;
            }
            pending.state = WorkState.RUNNING;
            cancelledBeforeStart = pending.cancelRequested;
        }
        try {
            if (!cancelledBeforeStart) {
                if (!cancellationRequested(pending)) {
                    worker.execute(pending.item);
                }
            }
        } catch (ExecutionLeaseLostException leaseLost) {
            // Recovery or another terminal writer already owns the final state.
        } catch (RuntimeException failure) {
            if (!cancellationRequested(pending)) {
                LOG.log(System.Logger.Level.ERROR,
                        "collection execution lease preparation failed for target {0}: {1}",
                        pending.item.targetId(), failure.getClass().getSimpleName());
                if (!failPersisted(pending, "LEASE_RENEWAL_FAILED")) {
                    preserveRecoveryBaseline(pending);
                }
            }
        } finally {
            if (cancellationRequested(pending) && !cancelPersisted(pending, pending.cancellationReason)) {
                preserveRecoveryBaseline(pending);
            }
            closeContext(pending);
            complete(pending);
        }
    }

    private void complete(PendingWork pending) {
        List<PendingWork> ready;
        synchronized (monitor) {
            if (pending.state == WorkState.FINISHED) {
                return;
            }
            releaseConnectionSlotLocked(pending);
            finishLocked(pending);
            KeyState state = keys.get(pending.key);
            ready = state == null ? List.of() : drainReadyLocked(pending.key, state);
            monitor.notifyAll();
        }
        dispatch(ready, null);
    }

    private void removeWaitingLocked(PendingWork pending) {
        KeyState state = keys.get(pending.key);
        if (state != null && state.waiting.remove(pending)) {
            waitingCount--;
            cleanupKeyLocked(pending.key, state);
        }
        if (pending.timeout != null) {
            pending.timeout.cancel(false);
        }
    }

    private void releaseConnectionSlotLocked(PendingWork pending) {
        KeyState state = keys.get(pending.key);
        if (state == null || state.active < 1) {
            throw new IllegalStateException("connection dispatcher active count is invalid");
        }
        state.active--;
        cleanupKeyLocked(pending.key, state);
    }

    private void cleanupKeyLocked(ConnectionKey key, KeyState state) {
        if (state.active == 0 && state.waiting.isEmpty()) {
            keys.remove(key, state);
        }
    }

    private void finishLocked(PendingWork pending) {
        if (pending.timeout != null) {
            pending.timeout.cancel(false);
        }
        pending.state = WorkState.FINISHED;
        outstandingWork.remove(pending);
        monitor.notifyAll();
    }

    private void beginFinalizationLocked(PendingWork pending) {
        if (pending.timeout != null) {
            pending.timeout.cancel(false);
        }
        pending.state = WorkState.FINALIZING;
    }

    private void finishFinalization(PendingWork pending) {
        synchronized (monitor) {
            if (pending.state == WorkState.FINALIZING) {
                finishLocked(pending);
            }
        }
    }

    private boolean failPersisted(PendingWork pending, String reason) {
        try {
            persistence.updateTarget(
                    pending.item.targetId(), pending.item.workerId(), CollectionStatus.FAILED,
                    "", "", null, reason, false, Map.of());
            return true;
        } catch (ExecutionLeaseLostException ignored) {
            // Another terminal writer or recovery sweep won the lease race.
            return true;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.ERROR,
                    "collection terminal persistence failed for target {0}: {1}",
                    pending.item.targetId(), failure.getClass().getSimpleName());
            return false;
        }
    }

    private boolean cancelPersisted(PendingWork pending, String reason) {
        try {
            persistence.cancelClaimedTarget(pending.item.targetId(), pending.item.workerId(), reason);
            return true;
        } catch (ExecutionLeaseLostException ignored) {
            // Completion or recovery already made the target terminal.
            return true;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.ERROR,
                    "collection cancellation persistence failed for target {0}: {1}",
                    pending.item.targetId(), failure.getClass().getSimpleName());
            return false;
        }
    }

    private boolean cancellationRequested(PendingWork pending) {
        synchronized (monitor) {
            return pending.cancelRequested;
        }
    }

    private void preserveRecoveryBaseline(PendingWork pending) {
        // Pending and running work is claimed with a short lease and kept alive by
        // the dispatcher-owned heartbeat batch. Stopping that batch is the durable,
        // zero-I/O handoff; shutdown never blocks on a new JDBC connection or commit.
        recordRecoverableHandoff(pending);
    }

    private void recordRecoverableHandoff(PendingWork pending) {
        synchronized (monitor) {
            if (pending.failedHandoff) {
                pending.failedHandoff = false;
                failedHandoffCount--;
            }
            if (!pending.recoveryHandoff) {
                pending.recoveryHandoff = true;
                recoverableHandoffCount++;
            }
        }
    }

    private void recordHandoffFailure(PendingWork pending) {
        synchronized (monitor) {
            recordHandoffFailureLocked(pending);
        }
    }

    private void recordHandoffFailureLocked(PendingWork pending) {
        if (!pending.recoveryHandoff && !pending.failedHandoff) {
            pending.failedHandoff = true;
            failedHandoffCount++;
        }
    }

    private void closeContext(PendingWork pending) {
        synchronized (monitor) {
            if (pending.contextClosed) {
                return;
            }
            pending.contextClosed = true;
        }
        try {
            pending.item.context().close();
        } catch (RuntimeException failure) {
            recordFinalizationFailure(pending);
            LOG.log(System.Logger.Level.ERROR,
                    "collection credential cleanup failed for target {0}: {1}",
                    pending.item.targetId(), failure.getClass().getSimpleName());
        }
    }

    private void recordFinalizationFailure(PendingWork pending) {
        synchronized (monitor) {
            if (pending.recoveryHandoff) {
                pending.recoveryHandoff = false;
                recoverableHandoffCount--;
            }
            if (!pending.failedHandoff) {
                pending.failedHandoff = true;
                failedHandoffCount++;
            }
        }
    }

    private WaitOutcome awaitOutstanding(long deadline) {
        synchronized (monitor) {
            while (!outstandingWork.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return new WaitOutcome(false, false);
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
                } catch (InterruptedException exception) {
                    return new WaitOutcome(false, true);
                }
            }
            return new WaitOutcome(true, false);
        }
    }

    private List<PendingWork> beginForcedShutdown() {
        List<PendingWork> neverStarted = new ArrayList<>();
        synchronized (monitor) {
            lifecycleState = LifecycleState.FORCED;
            for (PendingWork pending : List.copyOf(outstandingWork)) {
                pending.cancelRequested = true;
                if (pending.state == WorkState.CLAIMING) {
                    pending.claimInFlightAtForce = true;
                    recordHandoffFailureLocked(pending);
                    neverStarted.add(pending);
                } else if (pending.state == WorkState.WAITING) {
                    removeWaitingLocked(pending);
                    beginFinalizationLocked(pending);
                    neverStarted.add(pending);
                } else if (pending.state == WorkState.DISPATCHED && !pending.submittedToExecutor) {
                    pending.future.cancel(false);
                    releaseConnectionSlotLocked(pending);
                    beginFinalizationLocked(pending);
                    neverStarted.add(pending);
                }
            }
        }
        return neverStarted;
    }

    private List<PendingWork> terminalizeReturnedTasks(List<Runnable> returned) {
        List<PendingWork> neverStarted = new ArrayList<>();
        for (Runnable runnable : returned) {
            if (!(runnable instanceof PendingTask task)) {
                synchronized (monitor) {
                    unhandledReturnedTaskCount++;
                }
                continue;
            }
            PendingWork pending = task.pending;
            synchronized (monitor) {
                if (pending.state != WorkState.DISPATCHED) {
                    continue;
                }
                task.cancel(false);
                releaseConnectionSlotLocked(pending);
                beginFinalizationLocked(pending);
                neverStarted.add(pending);
            }
        }
        return neverStarted;
    }

    private void finalizeNeverStarted(PendingWork pending) {
        boolean terminalize;
        synchronized (monitor) {
            terminalize = pending.state == WorkState.FINALIZING
                    && !pending.claimInFlightAtForce;
        }
        if (terminalize) {
            preserveRecoveryBaseline(pending);
        }
        closeContext(pending);
        if (terminalize) {
            finishFinalization(pending);
        }
    }

    private ShutdownResult finishShutdown(
            ShutdownMode mode,
            boolean workerTerminated,
            boolean maintenanceTerminated,
            boolean interrupted) {
        synchronized (monitor) {
            lifecycleState = LifecycleState.TERMINATED;
            int unfinishedWork = outstandingWork.size();
            boolean acceptedWorkFinalized = unfinishedWork == 0
                    && recoverableHandoffCount == 0
                    && failedHandoffCount == 0
                    && unhandledReturnedTaskCount == 0;
            shutdownResult = new ShutdownResult(
                    mode,
                    workerTerminated,
                    maintenanceTerminated,
                    acceptedWorkFinalized,
                    interrupted,
                    recoverableHandoffCount,
                    failedHandoffCount,
                    unfinishedWork);
            return shutdownResult;
        }
    }

    private ShutdownResult currentShutdownResult() {
        ShutdownMode mode = lifecycleState == LifecycleState.GRACEFUL
                ? ShutdownMode.ORDERLY : ShutdownMode.FORCED;
        return currentShutdownResult(mode, false);
    }

    private ShutdownResult currentShutdownResult(ShutdownMode mode, boolean interrupted) {
        synchronized (monitor) {
            return new ShutdownResult(
                    mode,
                    executor.isTerminated(),
                    waitTimeouts.isTerminated(),
                    false,
                    interrupted,
                    recoverableHandoffCount,
                    failedHandoffCount,
                    outstandingWork.size());
        }
    }

    private static WaitOutcome awaitTermination(ExecutorService service, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) return new WaitOutcome(service.isTerminated(), false);
        try {
            return new WaitOutcome(service.awaitTermination(remaining, TimeUnit.NANOSECONDS), false);
        } catch (InterruptedException exception) {
            return new WaitOutcome(service.isTerminated(), true);
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private record ConnectionKey(ConnectionProtocol protocol, String host, int port, String username) {
        private static ConnectionKey from(CommandExecutionPort.ConnectionSpec connection) {
            return new ConnectionKey(
                    connection.protocol(),
                    connection.host().strip().toLowerCase(Locale.ROOT),
                    connection.port(),
                    connection.username().strip());
        }
    }

    private static final class KeyState {
        private final ArrayDeque<PendingWork> waiting = new ArrayDeque<>();
        private int active;
    }

    private static final class PendingWork {
        private final CollectionWorker.WorkItem item;
        private final ConnectionKey key;
        private WorkState state = WorkState.CLAIMING;
        private boolean cancelRequested;
        private String cancellationReason = SHUTDOWN_CANCELLED;
        private boolean recoveryHandoff;
        private boolean failedHandoff;
        private boolean claimInFlightAtForce;
        private boolean contextClosed;
        private boolean submittedToExecutor;
        private ScheduledFuture<?> timeout;
        private PendingTask future;

        private PendingWork(CollectionWorker.WorkItem item, ConnectionKey key) {
            this.item = item;
            this.key = key;
        }
    }

    private static final class PendingTask extends FutureTask<Void> {
        private final PendingWork pending;

        private PendingTask(PendingWork pending, Callable<Void> callable) {
            super(callable);
            this.pending = pending;
        }
    }

    private enum WorkState {
        CLAIMING,
        WAITING,
        DISPATCHED,
        RUNNING,
        FINALIZING,
        FINISHED
    }

    private enum LifecycleState {
        RUNNING,
        GRACEFUL,
        FORCED,
        TERMINATED
    }

    public enum ShutdownMode {
        ORDERLY,
        FORCED
    }

    private record WaitOutcome(boolean completed, boolean interrupted) {
    }

    public record ShutdownResult(
            ShutdownMode mode,
            boolean workerTerminated,
            boolean maintenanceTerminated,
            boolean acceptedWorkFinalized,
            boolean interrupted,
            int recoverableHandoffs,
            int failedHandoffs,
            int unfinishedWork) {
        public boolean orderly() {
            return mode == ShutdownMode.ORDERLY;
        }

        public boolean forced() {
            return mode == ShutdownMode.FORCED;
        }

        public boolean terminated() {
            return workerTerminated && maintenanceTerminated;
        }

        public boolean complete() {
            return terminated() && acceptedWorkFinalized;
        }
    }
}
