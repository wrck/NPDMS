package com.dp.deviceops.server;

import com.dp.deviceops.adapter.ssh.mina.MinaCommandExecutionAdapter;
import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import com.dp.deviceops.core.model.CollectionContextSnapshot;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CollectionTask;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.core.port.CollectionRepository;
import com.dp.deviceops.core.service.CollectionWorker;
import com.dp.deviceops.parser.runtime.port.ParseResultQueryPort;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.runtime.service.ParseTaskService;
import com.dp.deviceops.parser.runtime.service.ParserReleaseService;
import com.dp.deviceops.parser.runtime.service.ParserTaskExecutor;
import com.dp.deviceops.adapter.web.parser.concurrency.ParserWorkerCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceOpsSpringWiringTest {

    @Test
    void productionContextCreatesExecutorDispatcherWorkerMeteredPortAndSshWithoutCycles() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(DeviceOpsServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:wiring;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--device-ops.security.mode=local",
                        "--device-ops.runtime.auth-mode=local",
                        "--device-ops.executor.shutdown-await-seconds=1",
                        "--device-ops.executor.shutdown-graceful-period=500ms",
                        "--device-ops.executor.recovery-interval=50ms",
                        "--device-ops.executor.recovery-unclaimed-grace=1h");
        ThreadPoolExecutor executor = context.getBean("collectionExecutor", ThreadPoolExecutor.class);
        KeyedCollectionDispatcher dispatcher = context.getBean(KeyedCollectionDispatcher.class);
        try {
            assertNotNull(context.getBean(CollectionWorker.class));
            assertNotNull(context.getBean(MeteredCommandExecutionPort.class));
            assertNotNull(context.getBean(MinaCommandExecutionAdapter.class));
            assertNotNull(context.getBean(ParserReleaseRepository.class));
            assertNotNull(context.getBean(ParserPayloadStore.class));
            assertNotNull(context.getBean(ParseTaskRepository.class));
            assertNotNull(context.getBean(ParseResultQueryPort.class));
            assertNotNull(context.getBean(ParserReleaseService.class));
            assertNotNull(context.getBean(ParseTaskService.class));
            assertNotNull(context.getBean(ParserTaskExecutor.class));
            assertTrue(context.getBean(ParserWorkerCoordinator.class).isRunning());
            assertTrue(context.getBean(CollectionRuntimeLifecycle.class).isRunning());
            assertTrue(dispatcher.isAccepting());
            assertPeriodicRecoveryClosesExpiredJdbcLease(context);
        } finally {
            context.close();
        }
        assertFalse(dispatcher.isAccepting());
        assertTrue(executor.isShutdown());
    }

    private static void assertPeriodicRecoveryClosesExpiredJdbcLease(
            ConfigurableApplicationContext context) {
        String command = "show clock";
        CollectionTarget target = CollectionTarget.forSnapshot(
                CollectionContextSnapshot.of(
                        "pms", "project-a", "Project A", "PA",
                        "device-a", "Device A", "vendor", "model", Map.of()),
                "127.0.0.1", 22, "operator", null);
        CollectionTask task = CollectionTask.submitted(
                "spring-periodic-recovery", "pms", "project-a", "request-recovery", "idem-recovery",
                List.of(target),
                ScriptArtifact.adHoc(
                        "inline", "1", command, sha256(command), "NONE", null),
                "INSPECTION", URI.create("https://callback.example/events"));
        context.getBean(CollectionRepository.class).saveOrGetExisting(task);
        JdbcClient jdbc = context.getBean(JdbcClient.class);
        long targetId = jdbc.sql(
                        "select id from device_ops_collection_target where task_id='spring-periodic-recovery'")
                .query(Long.class).single();
        Instant now = Instant.now();
        assertTrue(context.getBean(CollectionExecutionPersistencePort.class)
                .claim(targetId, "expired-owner", now, now.plusMillis(250)));
        assertEquals("QUEUED", jdbc.sql("select status from device_ops_collection_target where id=:id")
                .param("id", targetId).query(String.class).single());

        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline && !"FAILED".equals(jdbc.sql(
                        "select status from device_ops_collection_target where id=:id")
                .param("id", targetId).query(String.class).single())) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        assertEquals("FAILED", jdbc.sql("select status from device_ops_collection_target where id=:id")
                .param("id", targetId).query(String.class).single());
        assertEquals(1, jdbc.sql(
                        "select count(*) from device_ops_outbox where aggregate_id='spring-periodic-recovery'")
                .query(Integer.class).single());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void springLifecycleForceClosesSshBeforeTheForcedTerminationAwait() {
        List<String> events = new CopyOnWriteArrayList<>();
        KeyedCollectionDispatcher dispatcher = mock(KeyedCollectionDispatcher.class);
        MinaCommandExecutionAdapter ssh = mock(MinaCommandExecutionAdapter.class);
        when(dispatcher.shutdown(any(Duration.class), any(Duration.class), any())).thenAnswer(invocation -> {
            events.add("shutdownNow");
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<KeyedCollectionDispatcher.ShutdownResult> forced = invocation.getArgument(2);
            forced.accept(new KeyedCollectionDispatcher.ShutdownResult(
                    KeyedCollectionDispatcher.ShutdownMode.FORCED,
                    false, true, false, false, 0, 0, 1));
            events.add("forced-await");
            return new KeyedCollectionDispatcher.ShutdownResult(
                    KeyedCollectionDispatcher.ShutdownMode.FORCED,
                    true, true, true, false, 0, 0, 0);
        });
        doAnswer(invocation -> {
            events.add("ssh");
            return null;
        }).when(ssh).forceClose();
        CollectionRuntimeLifecycle lifecycle = new CollectionRuntimeLifecycle(
                dispatcher, ssh, Duration.ofMillis(50), Duration.ofMillis(20));
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("dispatcher", KeyedCollectionDispatcher.class, () -> dispatcher,
                definition -> definition.setDestroyMethodName(""));
        context.registerBean("ssh", MinaCommandExecutionAdapter.class, () -> ssh,
                definition -> definition.setDestroyMethodName(""));
        context.registerBean("runtimeLifecycle", CollectionRuntimeLifecycle.class, () -> lifecycle);

        context.refresh();
        context.close();

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("shutdownNow", "ssh", "forced-await"), events);
        assertTrue(lifecycle.shutdownResult().terminated());
    }

    @Test
    void lifecycleRejectsGracefulSliceBeyondTheConfiguredTotalDeadline() {
        assertThrows(IllegalArgumentException.class, () -> new CollectionRuntimeLifecycle(
                mock(KeyedCollectionDispatcher.class),
                mock(MinaCommandExecutionAdapter.class),
                Duration.ofSeconds(10),
                Duration.ofSeconds(11)));
    }
}
