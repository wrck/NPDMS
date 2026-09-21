package com.dp.deviceops.adapter.web.concurrency;

import com.dp.deviceops.adapter.web.CollectionExecutionCoordinator;
import com.dp.deviceops.adapter.web.CollectionQueueFullException;
import com.dp.deviceops.adapter.web.CollectionSubmissionCoordinator;
import com.dp.deviceops.adapter.web.config.CollectionExecutorProperties;
import com.dp.deviceops.core.model.CollectionContextSnapshot;
import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.ExecutionConnectionContext;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.core.port.CollectionRepository;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.OutputParser;
import com.dp.deviceops.core.service.CollectionWorker;
import com.dp.deviceops.core.service.ExecutionLeaseLostException;
import com.dp.deviceops.core.service.OutputParserRegistry;
import com.dp.deviceops.core.service.SubmitCollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyedCollectionDispatcherTest {

    @Test
    void callerCanCancelWaitingAndRunningTargetsWithoutStoppingOtherWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        try (Fixture fixture = new Fixture(1, 1, 4, 1, 8, Duration.ofSeconds(10), (connection, script) -> {
            if (script.equals("running")) {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException cancelled) { interrupted.set(true); Thread.currentThread().interrupt(); }
                finally { stopped.countDown(); }
            }
        })) {
            fixture.submit(1, "device", "running");
            await(entered);
            fixture.submit(2, "device", "waiting");
            assertTrue(fixture.dispatcher.cancel(2));
            assertEquals(CollectionStatus.CANCELLED, fixture.persistence.status(2));
            assertEquals("CALLER_CANCELLED", fixture.persistence.outcome(2));
            assertTrue(fixture.dispatcher.cancel(1));
            assertFalse(stopped.await(100, TimeUnit.MILLISECONDS), "caller cancellation must not interrupt database work");
            release.countDown();
            await(stopped);
            assertFalse(interrupted.get());
            awaitCondition(() -> fixture.persistence.status(1) == CollectionStatus.CANCELLED);
            assertFalse(fixture.dispatcher.cancel(999));
            fixture.submit(3, "device", "remaining");
            awaitCondition(() -> fixture.persistence.status(3) == CollectionStatus.SUCCEEDED);
        } finally { release.countDown(); }
    }

    @Test
    void shutdownTimingValidationRejectsGraceBeyondTheTotalDeadline() {
        CollectionExecutorProperties properties = new CollectionExecutorProperties();
        properties.setShutdownAwaitSeconds(10);
        properties.setShutdownGracefulPeriod(Duration.ofSeconds(11));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, properties::validate);

        assertTrue(failure.getMessage().contains("shutdownGracefulPeriod"));
    }

    @Test
    void leaseValidationRequiresThreeHeartbeatIntervalsOfRecoveryMargin() {
        CollectionExecutorProperties properties = new CollectionExecutorProperties();
        properties.setLeaseHeartbeatInterval(Duration.ofSeconds(5));
        properties.setShutdownRecoveryLease(Duration.ofSeconds(14));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, properties::validate);

        assertTrue(failure.getMessage().contains("three leaseHeartbeatIntervals"));
    }

    @Test
    void leaseValidationCoversTheWholeShutdownDeadlineAndHeartbeatJitter() {
        CollectionExecutorProperties properties = new CollectionExecutorProperties();
        properties.setShutdownAwaitSeconds(30);
        properties.setShutdownGracefulPeriod(Duration.ofSeconds(10));
        properties.setLeaseHeartbeatInterval(Duration.ofSeconds(5));
        properties.setShutdownRecoveryLease(Duration.ofSeconds(35));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, properties::validate);

        assertTrue(failure.getMessage().contains("shutdownAwaitSeconds plus two leaseHeartbeatIntervals"));
    }

    @Test
    void saturatedEndpointDoesNotOccupyGlobalWorkerOrBlockAnotherEndpoint() throws Exception {
        CountDownLatch firstAStarted = new CountDownLatch(1);
        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstA = new CountDownLatch(1);
        List<String> starts = new java.util.concurrent.CopyOnWriteArrayList<>();
        try (Fixture fixture = new Fixture(2, 2, 4, 1, 6, Duration.ofSeconds(3), (connection, script) -> {
            starts.add(script);
            if (script.equals("A1")) {
                firstAStarted.countDown();
                await(releaseFirstA);
            } else if (script.equals("B1")) {
                bStarted.countDown();
            }
        })) {
            fixture.submit(1, "endpoint-a", "A1");
            assertTrue(firstAStarted.await(1, TimeUnit.SECONDS));
            fixture.submit(2, "endpoint-a", "A2");
            awaitValue(fixture.dispatcher::waitingCount, 1);

            fixture.submit(3, "endpoint-b", "B1");

            assertTrue(bStarted.await(500, TimeUnit.MILLISECONDS));
            assertFalse(releaseFirstA.await(20, TimeUnit.MILLISECONDS));
            assertEquals(1, fixture.dispatcher.waitingCount());
            releaseFirstA.countDown();
            awaitValue(fixture.dispatcher::outstandingCount, 0);
            assertTrue(starts.indexOf("A1") < starts.indexOf("A2"));
        } finally {
            releaseFirstA.countDown();
        }
    }

    @Test
    void configurableLimitRunsAtMostTwoAndPromotesSameKeyInFifoOrder() throws Exception {
        CountDownLatch firstTwoStarted = new CountDownLatch(2);
        CountDownLatch allStarted = new CountDownLatch(3);
        Semaphore releases = new Semaphore(0);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        List<String> starts = new java.util.concurrent.CopyOnWriteArrayList<>();
        try (Fixture fixture = new Fixture(3, 3, 3, 2, 6, Duration.ofSeconds(3), (connection, script) -> {
            starts.add(script);
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            firstTwoStarted.countDown();
            allStarted.countDown();
            try {
                assertTrue(releases.tryAcquire(2, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            } finally {
                active.decrementAndGet();
            }
        })) {
            fixture.submit(1, "same-endpoint", "first");
            fixture.submit(2, "same-endpoint", "second");
            assertTrue(firstTwoStarted.await(1, TimeUnit.SECONDS));
            fixture.submit(3, "same-endpoint", "third");
            awaitValue(fixture.dispatcher::waitingCount, 1);
            assertEquals(2, maximum.get());

            releases.release();
            assertTrue(allStarted.await(1, TimeUnit.SECONDS));
            assertEquals("third", starts.getLast());
            assertTrue(maximum.get() <= 2);
            releases.release(2);
            awaitValue(fixture.dispatcher::outstandingCount, 0);
        } finally {
            releases.release(3);
        }
    }

    @Test
    void totalOutstandingCapacityRejectsAsHttp429BeforeClaimAndScrubsCredentials() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 2, Duration.ofSeconds(3), (connection, script) -> {
            if (script.equals("active")) {
                activeStarted.countDown();
                await(release);
            }
        })) {
            fixture.submit(1, "endpoint", "active");
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));
            fixture.submit(2, "endpoint", "waiting");
            CollectionWorker.WorkItem rejected = fixture.item(3, "other", "rejected");

            CollectionExecutionCoordinator coordinator = new CollectionExecutionCoordinator(fixture.dispatcher);
            assertThrows(CollectionQueueFullException.class, () -> coordinator.submit(rejected));

            CollectionWorker.WorkItem httpRejected = fixture.item(4, "other", "http-rejected");
            CollectionRepository repository = new CollectionRepository() {
                @Override public Optional<com.dp.deviceops.core.model.CollectionTask> findByIdempotencyKey(
                        String namespace, String key) {
                    return Optional.empty();
                }
                @Override public SaveResult saveOrGetExisting(
                        com.dp.deviceops.core.model.CollectionTask candidate) {
                    return new SaveResult(candidate, true);
                }
            };
            CollectionSubmissionCoordinator submission = new CollectionSubmissionCoordinator(
                    new SubmitCollectionService(repository, () -> "collection-429"),
                    collectionId -> List.of(4L),
                    coordinator,
                    fixture.persistence,
                    httpRejected.parsers(),
                    null,
                    null);
            CollectionTarget target = CollectionTarget.forSnapshot(
                    CollectionContextSnapshot.ofOptional(null, null, Map.of()),
                    "other", 22, "operator", null);
            ResponseStatusException response = assertThrows(ResponseStatusException.class, () -> submission.submit(
                    new CollectionSubmissionCoordinator.Command(
                            "device-ops", null, null, "queue-full",
                            List.of(new CollectionSubmissionCoordinator.PreparedTarget(target, httpRejected.context())),
                            httpRejected.script(), null, null,
                            httpRejected.timeout(), httpRejected.parseTimeout(), httpRejected.leaseGrace())));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertEquals(List.of(4L), fixture.persistence.failedUnclaimedIds);
            assertEquals("QUEUE_FULL", fixture.persistence.failedUnclaimedReason.get());
            assertEquals(2, fixture.rejectedCount.get());
            assertFalse(fixture.persistence.claimedIds.contains(3L));
            assertFalse(fixture.persistence.claimedIds.contains(4L));
            assertThrows(IllegalStateException.class,
                    () -> rejected.context().withCredentials((secret, passphrase) -> null));
            assertThrows(IllegalStateException.class,
                    () -> httpRejected.context().withCredentials((secret, passphrase) -> null));
            release.countDown();
            awaitValue(fixture.dispatcher::outstandingCount, 0);
        } finally {
            release.countDown();
        }
    }

    @Test
    void waitingClaimStaysQueuedThenBecomesConnectingAtActualExecution() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(2, 2, 2, 1, 4, Duration.ofSeconds(3), (connection, script) -> {
            if (script.equals("first")) {
                firstStarted.countDown();
                await(release);
            } else {
                secondStarted.countDown();
            }
        })) {
            fixture.submit(1, "endpoint", "first");
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            fixture.submit(2, "endpoint", "second");
            awaitValue(fixture.dispatcher::waitingCount, 1);

            assertEquals(CollectionStatus.QUEUED, fixture.persistence.status(2));
            assertFalse(fixture.persistence.events(2).contains(CollectionStatus.CONNECTING));
            assertTrue(fixture.persistence.leaseUntil(2).isBefore(Instant.now().plusSeconds(1)),
                    "waiting work must keep a short recoverable lease refreshed by the owned heartbeat");

            release.countDown();
            assertTrue(secondStarted.await(1, TimeUnit.SECONDS));
            awaitCondition(() -> fixture.persistence.events(2).contains(CollectionStatus.CONNECTING));
            awaitValue(fixture.dispatcher::outstandingCount, 0);
        } finally {
            release.countDown();
        }
    }

    @Test
    void connectionWaitTimeoutIsDistinctAndNeverCallsProtocolAdapter() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger timedOutCalls = new AtomicInteger();
        try (Fixture fixture = new Fixture(2, 2, 2, 1, 4, Duration.ofMillis(80), (connection, script) -> {
            if (script.equals("first")) {
                firstStarted.countDown();
                await(release);
            } else {
                timedOutCalls.incrementAndGet();
            }
        })) {
            fixture.submit(1, "endpoint", "first");
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            fixture.submit(2, "endpoint", "timeout");

            awaitCondition(() -> KeyedCollectionDispatcher.CONNECTION_BUSY_TIMEOUT.equals(
                    fixture.persistence.outcome(2)));
            assertEquals(CollectionStatus.FAILED, fixture.persistence.status(2));
            assertEquals(0, timedOutCalls.get());
            assertEquals(1, fixture.dispatcher.timeoutCount());
            release.countDown();
            awaitValue(fixture.dispatcher::outstandingCount, 0);
        } finally {
            release.countDown();
        }
    }

    @Test
    void orderlyShutdownDrainsAcceptedWorkAndCompletesTaskFinalizers() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 2, Duration.ofSeconds(5), (connection, script) -> {
            activeStarted.countDown();
            await(release);
        })) {
            CollectionWorker.WorkItem active = fixture.item(1, "endpoint", "active");
            fixture.dispatcher.submit(active);
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));
            AtomicReference<KeyedCollectionDispatcher.ShutdownResult> result = new AtomicReference<>();
            Thread shutdown = Thread.ofPlatform().start(() -> result.set(fixture.dispatcher.shutdown(
                    Duration.ofSeconds(1), Duration.ofMillis(700), ignored -> {
                        throw new AssertionError("orderly shutdown must not enter forced cancellation");
                    })));
            awaitCondition(() -> !fixture.dispatcher.isAccepting());

            release.countDown();
            shutdown.join(Duration.ofSeconds(1));

            assertFalse(shutdown.isAlive());
            assertTrue(result.get().orderly());
            assertFalse(result.get().forced());
            assertTrue(result.get().terminated());
            assertTrue(result.get().complete());
            awaitCredentialClosed(active);
        } finally {
            release.countDown();
        }
    }

    @Test
    void forcedShutdownTerminalizesInterruptResponsiveActiveTaskBeforeReturning() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        AtomicBoolean forceAidCalled = new AtomicBoolean();
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 2, Duration.ofSeconds(5), (connection, script) -> {
            activeStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("cancelled", exception);
            }
        })) {
            CollectionWorker.WorkItem active = fixture.item(1, "endpoint", "active");
            fixture.dispatcher.submit(active);
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));

            KeyedCollectionDispatcher.ShutdownResult result = fixture.dispatcher.shutdown(
                    Duration.ofMillis(500), Duration.ofMillis(20), forced -> {
                        assertTrue(forced.forced());
                        forceAidCalled.set(true);
                    });

            assertTrue(forceAidCalled.get());
            assertTrue(result.forced());
            assertTrue(result.terminated());
            assertTrue(result.complete());
            assertEquals(CollectionStatus.CANCELLED, fixture.persistence.status(1));
            assertEquals(KeyedCollectionDispatcher.SHUTDOWN_CANCELLED, fixture.persistence.outcome(1));
            awaitCredentialClosed(active);
            int mutationsAtReturn = fixture.persistence.cancellationCount.get();
            Thread.sleep(50);
            assertEquals(mutationsAtReturn, fixture.persistence.cancellationCount.get(),
                    "terminated shutdown must not leave detached cleanup mutations");
        }
    }

    @Test
    void shutdownNowReturnsQueuedWorkForSynchronousCleanupAndRecoveryHandoff() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch releaseActive = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 2, 1, 3, Duration.ofSeconds(5), (connection, script) -> {
            if (script.equals("active")) {
                activeStarted.countDown();
                awaitIgnoringInterrupt(releaseActive);
            }
        })) {
            CollectionWorker.WorkItem active = fixture.item(1, "endpoint-a", "active");
            CollectionWorker.WorkItem sameKeyWaiting = fixture.item(2, "endpoint-a", "same-key-waiting");
            CollectionWorker.WorkItem executorQueued = fixture.item(3, "endpoint-b", "executor-queued");
            fixture.dispatcher.submit(active);
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));
            fixture.dispatcher.submit(sameKeyWaiting);
            fixture.dispatcher.submit(executorQueued);
            awaitValue(fixture.dispatcher::waitingCount, 1);

            KeyedCollectionDispatcher.ShutdownResult result = fixture.dispatcher.shutdown(
                    Duration.ofMillis(120), Duration.ofMillis(20), ignored -> { });

            assertTrue(result.forced());
            assertFalse(result.terminated());
            assertFalse(result.complete());
            assertEquals(2, result.recoverableHandoffs());
            assertEquals(CollectionStatus.QUEUED, fixture.persistence.status(2));
            assertEquals(CollectionStatus.QUEUED, fixture.persistence.status(3));
            awaitCredentialClosed(sameKeyWaiting);
            awaitCredentialClosed(executorQueued);
            int cancellationsAtReturn = fixture.persistence.cancellationCount.get();
            Thread.sleep(50);
            assertEquals(cancellationsAtReturn, fixture.persistence.cancellationCount.get(),
                    "shutdown must not leave detached pending-work cancellation");

            releaseActive.countDown();
            awaitCondition(() -> fixture.persistence.status(1) == CollectionStatus.CANCELLED);
            awaitCredentialClosed(active);
        } finally {
            releaseActive.countDown();
        }
    }

    @Test
    void interruptIgnoringActiveTaskReturnsNotTerminatedWithoutDetachedCleanup() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch releaseActive = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 2, Duration.ofSeconds(5), (connection, script) -> {
            activeStarted.countDown();
            awaitIgnoringInterrupt(releaseActive);
        })) {
            CollectionWorker.WorkItem active = fixture.item(1, "endpoint", "active");
            fixture.dispatcher.submit(active);
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));

            long started = System.nanoTime();
            KeyedCollectionDispatcher.ShutdownResult result = fixture.dispatcher.shutdown(
                    Duration.ofMillis(100), Duration.ofMillis(20), ignored -> { });
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

            assertTrue(result.forced());
            assertFalse(result.workerTerminated());
            assertFalse(result.terminated());
            assertFalse(result.complete());
            assertTrue(elapsedMillis < 500, () -> "shutdown took " + elapsedMillis + "ms");
            assertEquals(0, fixture.persistence.cancellationCount.get());
            Thread.sleep(50);
            assertEquals(0, fixture.persistence.cancellationCount.get(),
                    "active cleanup belongs to the still-running task wrapper");

            releaseActive.countDown();
            awaitCondition(() -> fixture.persistence.status(1) == CollectionStatus.CANCELLED);
            awaitCredentialClosed(active);
        } finally {
            releaseActive.countDown();
        }
    }

    @Test
    void shutdownThreadInterruptionForcesCancellationAndPreservesInterruptStatus() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch activeInterrupted = new CountDownLatch(1);
        CountDownLatch releaseActiveFinalizer = new CountDownLatch(1);
        CountDownLatch forcedPhaseEntered = new CountDownLatch(1);
        AtomicReference<KeyedCollectionDispatcher.ShutdownResult> result = new AtomicReference<>();
        AtomicBoolean interruptedOnReturn = new AtomicBoolean();
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 2, Duration.ofSeconds(5), (connection, script) -> {
            activeStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                activeInterrupted.countDown();
                awaitIgnoringInterrupt(releaseActiveFinalizer);
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("cancelled", exception);
            }
        })) {
            fixture.submit(1, "endpoint", "active");
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));
            Thread shutdown = Thread.ofPlatform().start(() -> {
                result.set(fixture.dispatcher.shutdown(
                        Duration.ofSeconds(2), Duration.ofSeconds(1), ignored -> forcedPhaseEntered.countDown()));
                interruptedOnReturn.set(Thread.currentThread().isInterrupted());
            });
            awaitCondition(() -> !fixture.dispatcher.isAccepting());

            shutdown.interrupt();
            assertTrue(activeInterrupted.await(1, TimeUnit.SECONDS));
            assertTrue(forcedPhaseEntered.await(1, TimeUnit.SECONDS));
            assertTrue(shutdown.isAlive(),
                    "an interrupted shutdown caller must still perform the forced termination await");
            releaseActiveFinalizer.countDown();
            shutdown.join(Duration.ofSeconds(1));

            assertFalse(shutdown.isAlive());
            assertTrue(result.get().forced());
            assertTrue(result.get().terminated());
            assertTrue(result.get().acceptedWorkFinalized());
            assertTrue(result.get().complete());
            assertTrue(result.get().interrupted());
            assertTrue(interruptedOnReturn.get());
        } finally {
            releaseActiveFinalizer.countDown();
        }
    }

    @Test
    void claimRacingShutdownUsesShortRecoverableHandoffWithoutDetachedCancellation() throws Exception {
        CountDownLatch releaseClaim = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 2, Duration.ofSeconds(5),
                (connection, script) -> { })) {
            fixture.persistence.claimBlock = releaseClaim;
            CollectionWorker.WorkItem claiming = fixture.item(1, "endpoint", "claiming");
            Thread submit = Thread.ofPlatform().start(() -> fixture.dispatcher.submit(claiming));
            assertTrue(fixture.persistence.claimStarted.await(1, TimeUnit.SECONDS));

            KeyedCollectionDispatcher.ShutdownResult result = fixture.dispatcher.shutdown(
                    Duration.ofMillis(100), Duration.ofMillis(20), ignored -> { });

            assertTrue(result.forced());
            assertFalse(result.complete());
            assertEquals(0, result.recoverableHandoffs());
            assertEquals(1, result.failedHandoffs());
            assertEquals(1, result.unfinishedWork());
            awaitCredentialClosed(claiming);
            assertEquals(0, fixture.persistence.cancellationCount.get());

            releaseClaim.countDown();
            submit.join(Duration.ofSeconds(1));
            assertFalse(submit.isAlive());
            assertTrue(fixture.persistence.leaseUntil(1).isBefore(Instant.now().plusSeconds(1)),
                    "the original submitter must shorten a delayed claim without detached cleanup");
            assertEquals(0, fixture.persistence.cancellationCount.get());
        } finally {
            releaseClaim.countDown();
        }
    }

    @Test
    void blockingMaintenanceIsReportedAsNotTerminatedWithinTheAbsoluteDeadline() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch releaseActive = new CountDownLatch(1);
        CountDownLatch releaseRenewal = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 2, Duration.ofSeconds(5), (connection, script) -> {
            activeStarted.countDown();
            awaitIgnoringInterrupt(releaseActive);
        })) {
            fixture.submit(1, "endpoint", "active");
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));
            fixture.persistence.renewalBlock = releaseRenewal;
            fixture.submit(2, "endpoint", "waiting");
            assertTrue(fixture.persistence.renewalStarted.await(1, TimeUnit.SECONDS));

            KeyedCollectionDispatcher.ShutdownResult result = fixture.dispatcher.shutdown(
                    Duration.ofMillis(100), Duration.ofMillis(20), ignored -> { });

            assertTrue(result.forced());
            assertFalse(result.maintenanceTerminated());
            assertFalse(result.terminated());
            releaseActive.countDown();
            releaseRenewal.countDown();
            assertTrue(fixture.persistence.renewalCompleted.await(1, TimeUnit.SECONDS));
        } finally {
            releaseActive.countDown();
            releaseRenewal.countDown();
        }
    }

    @Test
    void heartbeatBatchIncludesRunningWorkAndKeepsItsRecoveryLeaseLive() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch releaseActive = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 1, Duration.ofSeconds(5), (connection, script) -> {
            activeStarted.countDown();
            awaitIgnoringInterrupt(releaseActive);
        })) {
            fixture.submit(1, "endpoint", "active");
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));
            Instant initialLease = fixture.persistence.leaseUntil(1);

            assertTrue(fixture.persistence.renewalCompleted.await(1, TimeUnit.SECONDS));
            assertFalse(fixture.persistence.leaseUntil(1).isBefore(initialLease));
            assertTrue(fixture.persistence.leaseUntil(1).isBefore(Instant.now().plusSeconds(1)));
            releaseActive.countDown();
            awaitValue(fixture.dispatcher::outstandingCount, 0);
        } finally {
            releaseActive.countDown();
        }
    }

    @Test
    void lostRunningLeaseInterruptsTheWorkerAndLeavesContextCleanupToItsWrapper() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch releaseActive = new CountDownLatch(1);
        CollectionWorker.WorkItem[] active = new CollectionWorker.WorkItem[1];
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 1, Duration.ofSeconds(5), (connection, script) -> {
            activeStarted.countDown();
            try {
                releaseActive.await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        })) {
            active[0] = fixture.item(1, "endpoint", "active");
            fixture.dispatcher.submit(active[0]);
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));

            fixture.persistence.loseLease(1);

            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            awaitValue(fixture.dispatcher::outstandingCount, 0);
            awaitCredentialClosed(active[0]);
        } finally {
            releaseActive.countDown();
        }
    }

    @Test
    void lateHeartbeatAfterForcedHandoffKeepsTheDurableRecoveryWindowShort() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch releaseActive = new CountDownLatch(1);
        CountDownLatch releaseRenewal = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 2, Duration.ofSeconds(5), (connection, script) -> {
            if (script.equals("active")) {
                activeStarted.countDown();
                awaitIgnoringInterrupt(releaseActive);
            }
        })) {
            fixture.submit(1, "endpoint", "active");
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));
            fixture.persistence.renewalBlock = releaseRenewal;
            fixture.submit(2, "endpoint", "waiting");
            assertTrue(fixture.persistence.renewalStarted.await(1, TimeUnit.SECONDS));

            AtomicReference<KeyedCollectionDispatcher.ShutdownResult> result = new AtomicReference<>();
            Thread shutdown = Thread.ofPlatform().start(() -> result.set(fixture.dispatcher.shutdown(
                    Duration.ofSeconds(1), Duration.ofMillis(20), ignored -> releaseActive.countDown())));
            awaitValue(fixture.dispatcher::outstandingCount, 0);

            releaseRenewal.countDown();
            shutdown.join(Duration.ofSeconds(1));
            assertFalse(shutdown.isAlive());
            assertTrue(fixture.persistence.blockedRenewalCompleted.await(1, TimeUnit.SECONDS));
            assertTrue(fixture.persistence.leaseUntil(2).isBefore(Instant.now().plusSeconds(1)),
                    "a late heartbeat may only extend the pre-established short recovery lease");
            assertEquals(fixture.persistence.lastHeartbeatCalculatedAt.get().plusMillis(250),
                    fixture.persistence.leaseUntil(2),
                    "the full recovery window must be calculated after the blocked batch resumes");
            assertEquals("worker-2", fixture.persistence.leaseOwner(2));
            assertTrue(result.get().forced());
            assertEquals(1, result.get().recoverableHandoffs());
        } finally {
            releaseActive.countDown();
            releaseRenewal.countDown();
        }
    }

    @Test
    void forcedCancellationAidUnblocksActiveIoBeforeTheSecondTerminationAwait() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch forceClosed = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 2, Duration.ofSeconds(5), (connection, script) -> {
            activeStarted.countDown();
            awaitIgnoringInterrupt(forceClosed);
        })) {
            fixture.submit(1, "endpoint", "active-ssh-io");
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));

            KeyedCollectionDispatcher.ShutdownResult result = fixture.dispatcher.shutdown(
                    Duration.ofMillis(500), Duration.ofMillis(20), ignored -> forceClosed.countDown());

            assertTrue(result.forced());
            assertTrue(result.workerTerminated());
            assertTrue(result.terminated());
            assertTrue(result.complete());
            assertEquals(CollectionStatus.CANCELLED, fixture.persistence.status(1));
        } finally {
            forceClosed.countDown();
        }
    }

    @Test
    void rejectedAdmissionFinalizerRemainsOutstandingUntilCleanupCompletes() throws Exception {
        CountDownLatch releasePersistence = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 1, Duration.ofSeconds(5),
                (connection, script) -> { })) {
            fixture.executor.shutdown();
            fixture.persistence.updateBlock = releasePersistence;
            CollectionWorker.WorkItem rejected = fixture.item(1, "endpoint", "rejected");
            AtomicReference<Throwable> submissionFailure = new AtomicReference<>();
            Thread submit = Thread.ofPlatform().start(() -> {
                try {
                    fixture.dispatcher.submit(rejected);
                } catch (Throwable failure) {
                    submissionFailure.set(failure);
                }
            });
            assertTrue(fixture.persistence.updateStarted.await(1, TimeUnit.SECONDS));
            assertTrue(submit.isAlive());
            assertEquals(1, fixture.dispatcher.outstandingCount());

            KeyedCollectionDispatcher.ShutdownResult result = fixture.dispatcher.shutdown(
                    Duration.ofMillis(100), Duration.ofMillis(20), ignored -> { });

            assertTrue(result.forced());
            assertTrue(result.terminated());
            assertFalse(result.acceptedWorkFinalized());
            assertFalse(result.complete());
            assertEquals(1, result.unfinishedWork());

            releasePersistence.countDown();
            submit.join(Duration.ofSeconds(1));
            assertFalse(submit.isAlive());
            assertInstanceOf(RejectedExecutionException.class, submissionFailure.get());
            awaitCredentialClosed(rejected);
        } finally {
            releasePersistence.countDown();
        }
    }

    @Test
    void failedTerminalPersistenceIsReportedAsARecoveryHandoff() throws Exception {
        try (Fixture fixture = new Fixture(1, 1, 1, 1, 1, Duration.ofSeconds(5),
                (connection, script) -> { })) {
            fixture.executor.shutdown();
            fixture.persistence.updateFailure = true;
            CollectionWorker.WorkItem rejected = fixture.item(1, "endpoint", "rejected");

            assertThrows(RejectedExecutionException.class, () -> fixture.dispatcher.submit(rejected));
            KeyedCollectionDispatcher.ShutdownResult result = fixture.dispatcher.shutdown(
                    Duration.ofMillis(100), Duration.ofMillis(20), ignored -> { });

            assertFalse(result.acceptedWorkFinalized());
            assertFalse(result.complete());
            assertEquals(1, result.recoverableHandoffs());
            assertEquals(0, result.failedHandoffs());
            assertEquals(0, result.unfinishedWork());
            assertTrue(fixture.persistence.leaseUntil(1).isBefore(Instant.now().plusSeconds(1)));
            awaitCredentialClosed(rejected);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final RecordingPersistence persistence = new RecordingPersistence();
        private final AtomicLong rejectedCount = new AtomicLong();
        private final ThreadPoolExecutor executor;
        private final KeyedCollectionDispatcher dispatcher;

        private Fixture(
                int core,
                int max,
                int queue,
                int perConnectionLimit,
                int capacity,
                Duration waitTimeout,
                CommandBehavior behavior) {
            executor = new ThreadPoolExecutor(
                    core, max, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queue),
                    Thread.ofPlatform().name("dispatcher-test-", 0).factory(),
                    new ThreadPoolExecutor.AbortPolicy());
            CommandExecutionPort command = new CommandExecutionPort() {
                @Override public void test(ConnectionSpec connection, char[] secret, char[] passphrase) { }
                @Override public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                                       String script, Duration timeout) {
                    behavior.execute(connection, script);
                    return new CommandResult(0, script + "-output", "", false, false, 1,
                            List.of(com.dp.deviceops.core.model.CommandOutputBlock.legacy(
                                    script + "-output", "", 0, false, Map.of(), null)));
                }
            };
            OutputParser parser = new OutputParser() {
                @Override public String type() { return "plain"; }
                @Override public ParseResult parse(String rawOutput, String parserConfig) {
                    return new ParseResult(Map.of("raw", rawOutput), List.of());
                }
            };
            CollectionWorker worker = new CollectionWorker(
                    command, persistence, Clock.systemUTC());
            dispatcher = new KeyedCollectionDispatcher(
                    executor, worker, persistence, Clock.systemUTC(), waitTimeout,
                    Duration.ofMillis(20), Duration.ofMillis(250),
                    perConnectionLimit, capacity, rejectedCount);
        }

        private void submit(long id, String host, String script) {
            dispatcher.submit(item(id, host, script));
        }

        private CollectionWorker.WorkItem item(long id, String host, String script) {
            try {
                ExecutionConnectionContext context = new ExecutionConnectionContext(
                        new CommandExecutionPort.ConnectionSpec(
                                host, 22, "operator",
                                CommandExecutionPort.AuthenticationType.PASSWORD,
                                CommandExecutionPort.ExecutionMode.EXEC,
                                null, Duration.ofSeconds(1)),
                        new TransientCredential(("secret-" + id).toCharArray(), null));
                String sha = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8)));
                return new CollectionWorker.WorkItem(
                        id, "worker-" + id, context,
                        ScriptArtifact.adHoc("script-" + id, "v1", script, sha, "plain", ""),
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                        new OutputParserRegistry(List.of(new OutputParser() {
                            @Override public String type() { return "plain"; }
                            @Override public ParseResult parse(String rawOutput, String parserConfig) {
                                return new ParseResult(Map.of("raw", rawOutput), List.of());
                            }
                        })));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override public void close() {
            dispatcher.shutdown(Duration.ofSeconds(1));
            executor.shutdownNow();
        }
    }

    private static final class RecordingPersistence implements CollectionExecutionPersistencePort {
        private final Map<Long, CollectionStatus> statuses = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<Long, String> outcomes = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<Long, Instant> leases = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<Long, String> owners = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<Long, List<CollectionStatus>> statusEvents = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.Set<Long> claimedIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final List<Long> failedUnclaimedIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicReference<String> failedUnclaimedReason = new AtomicReference<>();
        private final AtomicReference<Instant> lastHeartbeatCalculatedAt = new AtomicReference<>();
        private final CountDownLatch cancellationStarted = new CountDownLatch(1);
        private final CountDownLatch claimStarted = new CountDownLatch(1);
        private final CountDownLatch renewalStarted = new CountDownLatch(1);
        private final CountDownLatch renewalCompleted = new CountDownLatch(1);
        private final CountDownLatch blockedRenewalCompleted = new CountDownLatch(1);
        private final CountDownLatch updateStarted = new CountDownLatch(1);
        private final AtomicInteger cancellationCount = new AtomicInteger();
        private volatile CountDownLatch cancellationBlock;
        private volatile CountDownLatch claimBlock;
        private volatile CountDownLatch renewalBlock;
        private volatile CountDownLatch updateBlock;
        private volatile boolean updateFailure;

        @Override public boolean claim(long targetId, String workerId, Instant startedAt, Instant leaseUntil) {
            return claim(targetId, workerId, () -> leaseUntil);
        }
        @Override public boolean claimPending(
                long targetId, String workerId, Instant startedAt, Duration leaseDuration) {
            return claim(targetId, workerId, () -> Instant.now().plus(leaseDuration));
        }
        private boolean claim(long targetId, String workerId, java.util.function.Supplier<Instant> leaseUntil) {
            CountDownLatch block = claimBlock;
            if (block != null) {
                claimStarted.countDown();
                awaitIgnoringInterrupt(block);
            }
            if (!claimedIds.add(targetId)) return false;
            statuses.put(targetId, CollectionStatus.QUEUED);
            statusEvents.computeIfAbsent(targetId, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(CollectionStatus.QUEUED);
            owners.put(targetId, workerId);
            leases.put(targetId, leaseUntil.get());
            return true;
        }
        @Override public void renewLease(long targetId, String workerId, Instant leaseUntil) {
            if (!workerId.equals(owners.get(targetId))) throw new ExecutionLeaseLostException(targetId);
            CountDownLatch block = renewalBlock;
            boolean maintenanceRenewal = Thread.currentThread().getName()
                    .startsWith("device-ops-connection-maintenance-");
            if (block != null && maintenanceRenewal) {
                renewalStarted.countDown();
                awaitIgnoringInterrupt(block);
            }
            try {
                if (!owners.replace(targetId, workerId, workerId)) {
                    throw new ExecutionLeaseLostException(targetId);
                }
                leases.put(targetId, leaseUntil);
            } finally {
                if (block != null && maintenanceRenewal) blockedRenewalCompleted.countDown();
            }
            renewalCompleted.countDown();
        }
        @Override public List<Long> renewPendingLeases(
                List<ClaimedTarget> targets, Instant leaseUntil) {
            return renewPendingLeases(targets, () -> leaseUntil);
        }
        @Override public List<Long> renewPendingLeases(
                List<ClaimedTarget> targets, Duration leaseDuration) {
            return renewPendingLeases(targets, () -> {
                Instant calculatedAt = Instant.now();
                lastHeartbeatCalculatedAt.set(calculatedAt);
                return calculatedAt.plus(leaseDuration);
            });
        }
        private List<Long> renewPendingLeases(
                List<ClaimedTarget> targets,
                java.util.function.Supplier<Instant> leaseUntil) {
            CountDownLatch block = renewalBlock;
            if (block != null) {
                renewalStarted.countDown();
                awaitIgnoringInterrupt(block);
            }
            Instant freshLeaseUntil = leaseUntil.get();
            List<Long> leaseLost = new ArrayList<>();
            try {
                for (ClaimedTarget target : targets) {
                    if (!owners.replace(target.targetId(), target.workerId(), target.workerId())) {
                        leaseLost.add(target.targetId());
                        continue;
                    }
                    leases.compute(target.targetId(), (ignored, current) ->
                            current == null || current.isBefore(freshLeaseUntil) ? freshLeaseUntil : current);
                }
                return List.copyOf(leaseLost);
            } finally {
                if (block != null) blockedRenewalCompleted.countDown();
                renewalCompleted.countDown();
            }
        }
        @Override public int failUnclaimedTargets(List<Long> targetIds, String reason) {
            failedUnclaimedIds.addAll(targetIds);
            failedUnclaimedReason.set(reason);
            return targetIds.size();
        }
        @Override public long appendOutput(long targetId, String workerId,
                                           int commandIndex,
                                           CommandExecutionPort.OutputStreamType streamType, String content,
                                           long receivedBytes, int pageCount, boolean truncated, Instant createdAt) {
            return 1;
        }
        @Override public void updateTarget(long targetId, String workerId, CollectionStatus status,
                                           String stdout, String stderr, Integer exitCode, String outcome,
                                           boolean truncated, Map<String, String> parsedFacts) {
            CountDownLatch block = updateBlock;
            if (block != null) {
                updateStarted.countDown();
                awaitIgnoringInterrupt(block);
            }
            if (updateFailure) throw new IllegalStateException("update failed");
            if (!workerId.equals(owners.get(targetId))) throw new ExecutionLeaseLostException(targetId);
            statuses.put(targetId, status);
            if (outcome != null) outcomes.put(targetId, outcome);
            statusEvents.computeIfAbsent(targetId, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(status);
        }
        @Override public int failRecoveredTargets(Instant now, String reason) { return 0; }
        @Override public void cancelClaimedTarget(long targetId, String workerId, String reason) {
            if (!workerId.equals(owners.get(targetId))) throw new ExecutionLeaseLostException(targetId);
            cancellationCount.incrementAndGet();
            cancellationStarted.countDown();
            CountDownLatch block = cancellationBlock;
            if (block != null) await(block);
            statuses.put(targetId, CollectionStatus.CANCELLED);
            outcomes.put(targetId, reason);
        }
        private CollectionStatus status(long id) { return statuses.get(id); }
        private String outcome(long id) { return outcomes.get(id); }
        private Instant leaseUntil(long id) { return leases.get(id); }
        private String leaseOwner(long id) { return owners.get(id); }
        private void loseLease(long id) { owners.remove(id); }
        private List<CollectionStatus> events(long id) {
            return new ArrayList<>(statusEvents.getOrDefault(id, List.of()));
        }

        private static void awaitIgnoringInterrupt(CountDownLatch latch) {
            boolean interrupted = false;
            while (latch.getCount() > 0) {
                try {
                    latch.await();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface CommandBehavior {
        void execute(CommandExecutionPort.ConnectionSpec connection, String script);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting for latch");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (latch.getCount() > 0) {
            try {
                latch.await();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static void awaitValue(IntSupplier value, int expected) throws InterruptedException {
        awaitCondition(() -> value.get() == expected);
        assertEquals(expected, value.get());
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.get() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(condition.get());
    }

    private static void awaitCredentialClosed(CollectionWorker.WorkItem item) throws InterruptedException {
        awaitCondition(() -> {
            try {
                item.context().withCredentials((secret, passphrase) -> null);
                return false;
            } catch (IllegalStateException expected) {
                return true;
            }
        });
    }

    @FunctionalInterface private interface IntSupplier { int get(); }
    @FunctionalInterface private interface BooleanSupplier { boolean get(); }
}
