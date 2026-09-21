package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.config.CollectionExecutorProperties;
import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionRecoveryOnReadyTest {

    @Test
    void startupAndPeriodicSweepUseBoundedStaleSafeRecoveryWindow() {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        AtomicReference<Instant> recoveredAt = new AtomicReference<>();
        AtomicReference<Instant> unclaimedBefore = new AtomicReference<>();
        AtomicReference<String> reason = new AtomicReference<>();
        AtomicInteger batchSize = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        CollectionExecutionPersistencePort persistence = new NoOpPersistence() {
            @Override public int failRecoverableTargets(
                    Instant current,
                    Instant staleBefore,
                    String failureReason,
                    int limit) {
                recoveredAt.set(current);
                unclaimedBefore.set(staleBefore);
                reason.set(failureReason);
                batchSize.set(limit);
                return calls.incrementAndGet();
            }
        };
        CollectionExecutorProperties properties = new CollectionExecutorProperties();
        properties.setRecoveryUnclaimedGrace(Duration.ofMinutes(2));
        properties.setRecoveryBatchSize(25);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        AtomicReference<Duration> interval = new AtomicReference<>();
        CollectionRecoveryOnReady recovery = new CollectionRecoveryOnReady(
                persistence, Clock.fixed(now, ZoneOffset.UTC), properties,
                (delay, maintenance) -> {
                    interval.set(delay);
                    scheduled.set(maintenance);
                    return true;
                });

        recovery.onApplicationEvent(null);
        scheduled.get().run();

        assertEquals(1, calls.get());
        assertEquals(properties.recoveryInterval(), interval.get());
        assertEquals(now, recoveredAt.get());
        assertEquals(now.minus(Duration.ofMinutes(2)), unclaimedBefore.get());
        assertEquals(CollectionRecoveryOnReady.TRANSIENT_CREDENTIAL_LOST, reason.get());
        assertEquals(25, batchSize.get());
    }

    private static class NoOpPersistence implements CollectionExecutionPersistencePort {
        @Override public boolean claim(long targetId, String workerId, Instant startedAt, Instant leaseUntil) { return false; }
        @Override public void renewLease(long targetId, String workerId, Instant leaseUntil) { }
        @Override public int failUnclaimedTargets(List<Long> targetIds, String reason) { return 0; }
        @Override public void updateTarget(long targetId, String workerId, CollectionStatus status,
                                           String stdout, String stderr, Integer exitCode, String outcome,
                                           boolean truncated, Map<String, String> parsedFacts) { }
        @Override public int failRecoveredTargets(Instant now, String reason) { return 0; }
    }
}
