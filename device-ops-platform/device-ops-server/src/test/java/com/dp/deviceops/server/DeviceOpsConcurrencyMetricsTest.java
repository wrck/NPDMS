package com.dp.deviceops.server;

import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import com.dp.deviceops.adapter.web.config.CollectionExecutorConfiguration;
import com.dp.deviceops.adapter.web.config.CollectionExecutorProperties;
import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.ExecutionConnectionContext;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.OutputParser;
import com.dp.deviceops.core.service.CollectionWorker;
import com.dp.deviceops.core.service.OutputParserRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceOpsConcurrencyMetricsTest {

    private static final Set<String> FORBIDDEN_TAG_FRAGMENTS = Set.of(
            "host", "username", "project", "device", "task", "target", "command",
            "credential", "secret", "passphrase", "connection");

    @Test
    void exposesDynamicLowCardinalityDispatcherMetricsAndCleansUpFromOutermostFinally() throws Exception {
        CollectionExecutorProperties properties = new CollectionExecutorProperties();
        properties.setCoreSize(1);
        properties.setMaxSize(1);
        properties.setQueueCapacity(1);
        properties.setPerConnectionLimit(1);
        properties.setConnectionWaitTimeout(Duration.ofMillis(250));
        properties.setLeaseHeartbeatInterval(Duration.ofMillis(20));
        AtomicLong rejectedCount = new AtomicLong();
        CollectionExecutorConfiguration configuration = new CollectionExecutorConfiguration();
        ThreadPoolExecutor executor = configuration.collectionExecutor(properties, rejectedCount);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch releaseActive = new CountDownLatch(1);
        KeyedCollectionDispatcher dispatcher = dispatcher(
                executor, properties, rejectedCount, activeStarted, releaseActive);
        try {
            new DeviceOpsConcurrencyMetrics(registry, executor, dispatcher, rejectedCount);

            assertGauge(registry, "device_ops_collection_active", 0.0);
            assertGauge(registry, "device_ops_collection_queued", 0.0);
            assertGauge(registry, "device_ops_collection_queue_remaining", 2.0);
            assertGauge(registry, "device_ops_connection_waiting", 0.0);
            assertGauge(registry, "device_ops_connection_keys_active", 0.0);

            dispatcher.submit(item(1, "metrics.example", "active"));
            assertTrue(activeStarted.await(1, TimeUnit.SECONDS));
            dispatcher.submit(item(2, "metrics.example", "waiting"));
            awaitValue(dispatcher::waitingCount, 1);
            CollectionWorker.WorkItem rejected = item(3, "other.example", "rejected");
            assertThrows(java.util.concurrent.RejectedExecutionException.class,
                    () -> dispatcher.submit(rejected));
            rejected.context().close();

            assertGauge(registry, "device_ops_collection_active", 1.0);
            assertGauge(registry, "device_ops_collection_queued", 0.0);
            assertGauge(registry, "device_ops_collection_queue_remaining", 0.0);
            assertGauge(registry, "device_ops_connection_waiting", 1.0);
            assertGauge(registry, "device_ops_connection_keys_active", 1.0);
            assertEquals(1.0, registry.get("device_ops_collection_rejected_total")
                    .functionCounter().count());

            awaitValue(dispatcher::waitingCount, 0);
            assertEquals(1.0, registry.get("device_ops_connection_wait_timeout_total")
                    .functionCounter().count());
            registry.getMeters().forEach(DeviceOpsConcurrencyMetricsTest::assertSafeTags);
            registry.getMeters().stream()
                    .filter(meter -> meter.getId().getName().startsWith("device_ops_collection_")
                            || meter.getId().getName().startsWith("device_ops_connection_"))
                    .forEach(meter -> assertTrue(meter.getId().getTags().isEmpty()));
        } finally {
            releaseActive.countDown();
            dispatcher.shutdown(Duration.ofSeconds(1));
            executor.shutdownNow();
            registry.close();
        }
    }

    private static KeyedCollectionDispatcher dispatcher(
            ThreadPoolExecutor executor,
            CollectionExecutorProperties properties,
            AtomicLong rejectedCount,
            CountDownLatch activeStarted,
            CountDownLatch releaseActive) {
        CollectionExecutionPersistencePort persistence = new NoOpPersistence();
        CommandExecutionPort command = new CommandExecutionPort() {
            @Override public void test(ConnectionSpec connection, char[] secret, char[] passphrase) { }
            @Override public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                                   String script, Duration timeout) {
                if (script.equals("active")) {
                    activeStarted.countDown();
                    await(releaseActive);
                }
                return new CommandResult(0, script, "", false, false, 1,
                        List.of(com.dp.deviceops.core.model.CommandOutputBlock.legacy(
                                script, "", 0, false, Map.of(), null)));
            }
        };
        return new KeyedCollectionDispatcher(
                executor,
                new CollectionWorker(command, persistence, Clock.systemUTC()),
                persistence,
                Clock.systemUTC(),
                properties.connectionWaitTimeout(),
                properties.leaseHeartbeatInterval(),
                properties.shutdownRecoveryLease(),
                properties.perConnectionLimit(),
                properties.outstandingCapacity(),
                rejectedCount);
    }

    private static CollectionWorker.WorkItem item(long id, String host, String script) throws Exception {
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8)));
        OutputParser parser = new OutputParser() {
            @Override public String type() { return "plain"; }
            @Override public ParseResult parse(String rawOutput, String parserConfig) {
                return new ParseResult(Map.of(), List.of());
            }
        };
        return new CollectionWorker.WorkItem(
                id,
                "worker-" + id,
                new ExecutionConnectionContext(
                        new CommandExecutionPort.ConnectionSpec(
                                host, 22, "operator",
                                CommandExecutionPort.AuthenticationType.PASSWORD,
                                CommandExecutionPort.ExecutionMode.EXEC,
                                null, Duration.ofSeconds(1)),
                        new TransientCredential("secret".toCharArray(), null)),
                ScriptArtifact.adHoc("script", "v1", script, hash, "plain", ""),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                new OutputParserRegistry(List.of(parser)));
    }

    private static void assertGauge(SimpleMeterRegistry registry, String name, double expected) {
        assertEquals(expected, registry.get(name).gauge().value());
    }

    private static void assertSafeTags(Meter meter) {
        meter.getId().getTags().forEach(tag -> {
            String key = tag.getKey().toLowerCase(Locale.ROOT);
            assertTrue(FORBIDDEN_TAG_FRAGMENTS.stream().noneMatch(key::contains),
                    () -> "forbidden metric tag key: " + tag.getKey());
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting for latch");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void awaitValue(IntSupplier supplier, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (supplier.get() != expected && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(expected, supplier.get());
    }

    private static class NoOpPersistence implements CollectionExecutionPersistencePort {
        private final Set<Long> claimed = java.util.concurrent.ConcurrentHashMap.newKeySet();
        @Override public boolean claim(long targetId, String workerId, Instant startedAt, Instant leaseUntil) {
            return claimed.add(targetId);
        }
        @Override public void renewLease(long targetId, String workerId, Instant leaseUntil) { }
        @Override public int failUnclaimedTargets(List<Long> targetIds, String reason) { return 0; }
        @Override public long appendOutput(long targetId, String workerId,
                                           int commandIndex,
                                           CommandExecutionPort.OutputStreamType streamType, String content,
                                           long receivedBytes, int pageCount, boolean truncated, Instant createdAt) { return 1; }
        @Override public void updateTarget(long targetId, String workerId, CollectionStatus status,
                                           String stdout, String stderr, Integer exitCode, String outcome,
                                           boolean truncated, Map<String, String> parsedFacts) { }
        @Override public int failRecoveredTargets(Instant now, String reason) { return 0; }
    }

    @FunctionalInterface private interface IntSupplier { int get(); }
}
