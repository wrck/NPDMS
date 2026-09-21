package com.dp.deviceops.server;

import com.dp.deviceops.adapter.ssh.mina.MinaCommandExecutionAdapter;
import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates orderly drain and best-effort forced cancellation under one shutdown deadline. */
public final class CollectionRuntimeLifecycle implements SmartLifecycle {

    private static final System.Logger LOG = System.getLogger(CollectionRuntimeLifecycle.class.getName());
    private final KeyedCollectionDispatcher dispatcher;
    private final MinaCommandExecutionAdapter ssh;
    private final Duration shutdownAwait;
    private final Duration gracefulSlice;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile KeyedCollectionDispatcher.ShutdownResult shutdownResult;

    public CollectionRuntimeLifecycle(
            KeyedCollectionDispatcher dispatcher,
            MinaCommandExecutionAdapter ssh,
            Duration shutdownAwait,
            Duration gracefulSlice) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.ssh = Objects.requireNonNull(ssh, "ssh");
        this.shutdownAwait = Objects.requireNonNull(shutdownAwait, "shutdownAwait");
        Objects.requireNonNull(gracefulSlice, "gracefulSlice");
        if (shutdownAwait.isNegative() || gracefulSlice.isNegative()) {
            throw new IllegalArgumentException("shutdown durations must not be negative");
        }
        if (gracefulSlice.compareTo(shutdownAwait) > 0) {
            throw new IllegalArgumentException("gracefulSlice must not exceed shutdownAwait");
        }
        this.gracefulSlice = gracefulSlice;
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                shutdownResult = dispatcher.shutdown(shutdownAwait, gracefulSlice, forced -> {
                    if (forced.forced()) {
                        // Forced close actively unblocks Mina I/O before the dispatcher's second await.
                        ssh.forceClose();
                    }
                });
                if (shutdownResult.orderly()) {
                    ssh.close();
                }
                if (shutdownResult.forced() && shutdownResult.complete()) {
                    LOG.log(System.Logger.Level.INFO,
                            "collection runtime terminated after best-effort forced cancellation");
                } else if (!shutdownResult.complete()) {
                    LOG.log(System.Logger.Level.WARNING,
                            "collection shutdown incomplete: workersTerminated={0}, maintenanceTerminated={1}, "
                                    + "recoverableHandoffs={2}, failedHandoffs={3}, "
                                    + "unfinishedWork={4}, interrupted={5}",
                            shutdownResult.workerTerminated(),
                            shutdownResult.maintenanceTerminated(),
                            shutdownResult.recoverableHandoffs(),
                            shutdownResult.failedHandoffs(),
                            shutdownResult.unfinishedWork(),
                            shutdownResult.interrupted());
                }
            } catch (RuntimeException failure) {
                ssh.forceClose();
                throw failure;
            }
        }
    }

    KeyedCollectionDispatcher.ShutdownResult shutdownResult() {
        return shutdownResult;
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
