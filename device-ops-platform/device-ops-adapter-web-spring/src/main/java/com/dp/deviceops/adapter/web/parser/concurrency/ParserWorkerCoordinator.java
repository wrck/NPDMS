package com.dp.deviceops.adapter.web.parser.concurrency;

import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.ClaimedTask;
import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
import com.dp.deviceops.parser.runtime.service.ParserTaskExecutor;
import org.springframework.context.SmartLifecycle;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Claims only work for which local executor capacity has already been reserved. */
public final class ParserWorkerCoordinator implements SmartLifecycle {

    private static final System.Logger LOG = System.getLogger(ParserWorkerCoordinator.class.getName());
    private final String workerId;
    private final Set<WorkerCapability> capabilities;
    private final ParseTaskRepository tasks;
    private final ParserTaskExecutor taskExecutor;
    private final ThreadPoolExecutor executor;
    private final ParserWorkerProperties properties;
    private final Clock clock;
    private final Semaphore permits;
    private final ConcurrentMap<TaskLease, Boolean> active = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();

    public ParserWorkerCoordinator(String workerId, Set<WorkerCapability> capabilities,
            ParseTaskRepository tasks, ParserTaskExecutor taskExecutor, ThreadPoolExecutor executor,
            ParserWorkerProperties properties, Clock clock) {
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.capabilities = Set.copyOf(capabilities);
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        properties.validate();
        this.permits = new Semaphore(properties.outstandingCapacity());
    }

    public void poll() {
        if (!running.get()) return;
        int acquired = acquireAvailable(properties.claimBatchSize());
        if (acquired == 0) return;
        List<ClaimedTask> claimed;
        Instant now = clock.instant();
        try {
            claimed = tasks.claim(workerId, capabilities, now, now.plus(properties.lease()), acquired);
        } catch (RuntimeException failure) {
            permits.release(acquired);
            throw failure;
        }
        if (claimed.size() > acquired) {
            permits.release(acquired);
            throw new IllegalStateException("task repository returned more claims than requested");
        }
        permits.release(acquired - claimed.size());
        for (ClaimedTask task : claimed) {
            dispatch(task, now);
        }
    }

    public void renewActiveLeases(Instant leaseUntil) {
        for (TaskLease lease : active.keySet()) {
            try {
                tasks.renew(lease.taskId(), workerId, lease.generation(), leaseUntil);
            } catch (ParserRuntimeError error) {
                if ("PARSER_LEASE_LOST".equals(error.code())) {
                    active.remove(lease);
                } else {
                    throw error;
                }
            }
        }
    }

    public Set<WorkerCapability> capabilities() { return capabilities; }
    public String workerId() { return workerId; }
    public int activeCount() { return active.size(); }
    public int queuedCount() { return executor.getQueue().size(); }
    public int availablePermits() { return permits.availablePermits(); }

    @Override public void start() { running.set(true); }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        try {
            renewActiveLeases(clock.instant().plus(properties.shutdownGrace()));
        } catch (RuntimeException renewalFailure) {
            LOG.log(System.Logger.Level.WARNING, "parser shutdown lease renewal failed: {0}",
                    renewalFailure.getClass().getSimpleName());
        } finally {
            executor.shutdown();
        }
        try {
            executor.awaitTermination(properties.shutdownGrace().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override public boolean isRunning() { return running.get(); }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 10; }
    @Override public void stop(Runnable callback) { try { stop(); } finally { callback.run(); } }

    private int acquireAvailable(int maximum) {
        int acquired = 0;
        while (acquired < maximum && permits.tryAcquire()) acquired++;
        return acquired;
    }

    private void dispatch(ClaimedTask task, Instant now) {
        TaskLease lease = new TaskLease(task.taskId(), task.leaseGeneration());
        active.put(lease, Boolean.TRUE);
        try {
            executor.execute(() -> {
                try {
                    taskExecutor.execute(task);
                } finally {
                    active.remove(lease);
                    permits.release();
                }
            });
        } catch (RejectedExecutionException rejected) {
            active.remove(lease);
            try {
                tasks.releaseClaim(task.taskId(), workerId, task.leaseGeneration(), now);
            } finally {
                permits.release();
            }
            LOG.log(System.Logger.Level.WARNING, "parser task queue rejected taskId={0}", task.taskId());
        }
    }

    private record TaskLease(String taskId, long generation) { }
}
