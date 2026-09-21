package com.dp.deviceops.adapter.web.parser.concurrency;

import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.service.ParserTaskExecutor;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParserWorkerCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final Set<WorkerCapability> CAPABILITIES = Set.of(
            new WorkerCapability("1.0.0", null, null));

    @Test
    void configurationAdvertisesLegacyAndCurrentEngines() {
        Set<WorkerCapability> capabilities = new ParserWorkerConfiguration().parserWorkerCapabilities();

        assertTrue(capabilities.contains(new WorkerCapability(ParserPlanCompiler.LEGACY_ENGINE_VERSION, null, null)));
        assertTrue(capabilities.contains(new WorkerCapability(ParserPlanCompiler.ENGINE_VERSION, null, null)));
        assertTrue(capabilities.contains(new WorkerCapability(
                ParserPlanCompiler.NESTED_ENGINE_VERSION, null, null)));
        assertTrue(capabilities.contains(new WorkerCapability(
                ParserPlanCompiler.STRUCTURED_ENGINE_VERSION, null, null)));
        assertTrue(capabilities.contains(new WorkerCapability(
                ParserPlanCompiler.ENHANCED_ENGINE_VERSION, null, null)));
        assertEquals(Set.of("1.0.0", "1.1.0", "1.2.0", "1.3.0", "1.4.0"), capabilities.stream()
                .filter(capability -> capability.extensionId() == null)
                .map(WorkerCapability::engineVersion)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void permitsPreventOverClaimAndShutdownRenewsActiveLeases() throws Exception {
        ParserWorkerProperties properties = properties(1, 1, 2);
        ParseTaskRepository tasks = mock(ParseTaskRepository.class);
        ParserTaskExecutor taskExecutor = mock(ParserTaskExecutor.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(taskExecutor).execute(any());
        when(tasks.claim(anyString(), anySet(), any(), any(), eq(2)))
                .thenReturn(List.of(task("task-1", 1), task("task-2", 1)));
        ThreadPoolExecutor pool = pool(1, 1);
        ParserWorkerCoordinator coordinator = coordinator(tasks, taskExecutor, pool, properties);
        coordinator.start();

        coordinator.poll();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        coordinator.poll();
        assertEquals(0, coordinator.availablePermits());
        verify(tasks).claim(anyString(), anySet(), any(), any(), eq(2));

        coordinator.stop();
        verify(tasks).renew(eq("task-1"), eq("worker-1"), eq(1L), any());
        verify(tasks).renew(eq("task-2"), eq("worker-1"), eq(1L), any());
        release.countDown();
    }

    @Test
    void rejectedLocalQueueReleasesDatabaseClaimAndPermit() {
        ParserWorkerProperties properties = properties(1, 0, 1);
        ParseTaskRepository tasks = mock(ParseTaskRepository.class);
        when(tasks.claim(anyString(), anySet(), any(), any(), eq(1)))
                .thenReturn(List.of(task("task-1", 4)));
        ThreadPoolExecutor rejectedPool = pool(1, 1);
        rejectedPool.shutdown();
        ParserWorkerCoordinator coordinator = coordinator(tasks, mock(ParserTaskExecutor.class),
                rejectedPool, properties);
        coordinator.start();

        coordinator.poll();

        verify(tasks).releaseClaim("task-1", "worker-1", 4, NOW);
        assertEquals(1, coordinator.availablePermits());
        assertEquals(0, coordinator.activeCount());
        verify(tasks, never()).renew(anyString(), anyString(), anyLong(), any());
    }

    private static ParserWorkerCoordinator coordinator(ParseTaskRepository tasks,
            ParserTaskExecutor taskExecutor, ThreadPoolExecutor pool, ParserWorkerProperties properties) {
        return new ParserWorkerCoordinator("worker-1", CAPABILITIES, tasks, taskExecutor, pool,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ParserWorkerProperties properties(int max, int queue, int batch) {
        ParserWorkerProperties properties = new ParserWorkerProperties();
        properties.setCoreSize(1);
        properties.setMaxSize(max);
        properties.setQueueCapacity(queue);
        properties.setClaimBatchSize(batch);
        properties.setHeartbeatInterval(Duration.ofSeconds(1));
        properties.setLease(Duration.ofSeconds(3));
        properties.setShutdownGrace(Duration.ofMillis(10));
        return properties;
    }

    private static ThreadPoolExecutor pool(int threads, int queue) {
        return new ThreadPoolExecutor(threads, threads, 1, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ParseTaskRepository.ClaimedTask task(String taskId, long generation) {
        return new ParseTaskRepository.ClaimedTask(taskId, "release-1",
                new ParserCoordinate("runtime-log", "1.0.0", "1.0.0", "1.0.0", "1.0.0", null, null),
                "payload-1", generation);
    }
}
