package com.dp.deviceops.core.terminal;

import com.dp.deviceops.core.model.ConnectionFailure;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Coordinates terminal pager input after, and only after, concrete pager evidence was observed.
 */
public final class TerminalPagerController {

    private final long settleNanos;
    private final int maxContinuations;
    private final LongSupplier nanoTime;

    private boolean pagerActive;
    private boolean continuationPending;
    private long continuationReadyAt = Long.MAX_VALUE;
    private int continuationCount;
    private int pageCount;

    public TerminalPagerController(Duration settleTime, int maxContinuations) {
        this(settleTime, maxContinuations, System::nanoTime);
    }

    TerminalPagerController(Duration settleTime, int maxContinuations, LongSupplier nanoTime) {
        Objects.requireNonNull(settleTime, "settleTime");
        if (settleTime.isNegative()) {
            throw new IllegalArgumentException("settleTime must not be negative");
        }
        if (maxContinuations < 1) {
            throw new IllegalArgumentException("maxContinuations must be positive");
        }
        this.settleNanos = settleTime.toNanos();
        this.maxContinuations = maxContinuations;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public void observe(TerminalTextProcessor.Decision decision) {
        Objects.requireNonNull(decision, "decision");
        pageCount = Math.max(pageCount, decision.pageCount());
        if (decision.promptReached()) {
            pagerActive = false;
            continuationPending = false;
            continuationReadyAt = Long.MAX_VALUE;
            return;
        }
        if (decision.sendContinue()) {
            pagerActive = true;
        }
        if (pagerActive && !continuationPending) {
            continuationPending = true;
            continuationReadyAt = saturatingAdd(nanoTime.getAsLong(), settleNanos);
        }
    }

    public boolean continuationReady() {
        return continuationPending && nanoTime.getAsLong() - continuationReadyAt >= 0;
    }

    public void markContinuationSent() {
        if (!continuationPending) {
            throw new IllegalStateException("terminal continuation is not pending");
        }
        continuationPending = false;
        continuationReadyAt = Long.MAX_VALUE;
        continuationCount++;
        if (continuationCount > maxContinuations) {
            throw new ConnectionFailure(ConnectionFailure.Code.CONNECTION_CLOSED,
                    ConnectionFailure.Stage.EXECUTE, "pagination limit exceeded");
        }
        pageCount = Math.max(pageCount, continuationCount);
    }

    public int pageCount() {
        return pageCount;
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }
}
