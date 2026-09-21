package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionContextSnapshot;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CollectionTask;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.port.CommandExecutionPort.OutputStreamType;
import com.dp.deviceops.core.service.ExecutionLeaseLostException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
class CollectionExecutionConcurrencyJdbcTest {

    @Test
    void concurrentWorkersCreateExactlyOneClaimAndAttempt() throws Exception {
        Fixture fixture = fixture("claim-race");
        long targetId = fixture.targetIds().getFirst();
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        CyclicBarrier start = new CyclicBarrier(3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return fixture.execution().claim(targetId, "worker-a", now, now.plusSeconds(30));
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return fixture.execution().claim(targetId, "worker-b", now, now.plusSeconds(30));
            });
            start.await();

            assertTrue(first.get() ^ second.get());
        }
        assertEquals(1, fixture.jdbc().sql("select count(*) from device_ops_collection_attempt where target_id=:id")
                .param("id", targetId).query(Integer.class).single());
        assertTrue(List.of("worker-a", "worker-b").contains(
                fixture.jdbc().sql("select lease_owner from device_ops_collection_target where id=:id")
                        .param("id", targetId).query(String.class).single()));
    }

    @Test
    void leaseRenewalAndRecoveryRaceHasOneConsistentWinner() throws Exception {
        Fixture fixture = fixture("renew-reaper-race");
        long targetId = fixture.targetIds().getFirst();
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        assertTrue(fixture.execution().claim(targetId, "worker", now.minusSeconds(5), now.minusSeconds(1)));
        CyclicBarrier start = new CyclicBarrier(3);
        boolean renewed;
        int recovered;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> renew = executor.submit(() -> {
                start.await();
                try {
                    fixture.execution().renewLease(targetId, "worker", now.plusSeconds(60));
                    return true;
                } catch (ExecutionLeaseLostException lost) {
                    return false;
                }
            });
            Future<Integer> reaper = executor.submit(() -> {
                start.await();
                return fixture.execution().failRecoverableTargets(
                        now, now.minusSeconds(120), "TRANSIENT_CREDENTIAL_LOST", 10);
            });
            start.await();
            renewed = renew.get();
            recovered = reaper.get();
        }

        Map<String, Object> target = fixture.jdbc().sql(
                        "select status,lease_owner from device_ops_collection_target where id=:id")
                .param("id", targetId).query().singleRow();
        if (renewed) {
            assertEquals(0, recovered);
            assertEquals("QUEUED", target.get("STATUS"));
            assertEquals("worker", target.get("LEASE_OWNER"));
            assertEquals(0, fixture.outboxCount());
        } else {
            assertEquals(1, recovered);
            assertEquals("FAILED", target.get("STATUS"));
            assertNull(target.get("LEASE_OWNER"));
            assertEquals(1, fixture.outboxCount());
        }
    }

    @Test
    void streamingAppendAndSnapshotRaceWithCancellationPreservesWinningEvidence() throws Exception {
        Fixture fixture = fixture("append-cancel-race");
        long targetId = fixture.targetIds().getFirst();
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        assertTrue(fixture.execution().claim(targetId, "worker", now, now.plusSeconds(60)));
        CyclicBarrier start = new CyclicBarrier(3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> writer = executor.submit(() -> {
                start.await();
                try {
                    fixture.execution().appendOutput(
                            targetId, "worker", 1, OutputStreamType.STDOUT,
                            "streamed-output", 15, 2, true, now);
                    fixture.execution().updateOutputSnapshot(
                            targetId, "worker", "streamed-output", "", true);
                    return true;
                } catch (ExecutionLeaseLostException lost) {
                    return false;
                }
            });
            Future<?> cancel = executor.submit(() -> {
                start.await();
                fixture.execution().cancelClaimedTarget(targetId, "worker", "SHUTDOWN_CANCELLED");
                return null;
            });
            start.await();
            writer.get();
            cancel.get();
        }

        assertTerminalEvidenceMatchesEvents(fixture, targetId, "CANCELLED");
        assertEquals(1, fixture.outboxCount());
        String payload = fixture.onlyOutboxPayload();
        assertTrue(payload.contains("CANCELLED"));
        assertEquals(fixture.stdout(targetId).contains("streamed-output"), payload.contains("streamed-output"));
    }

    @Test
    void streamingAppendAndSnapshotRaceWithRecoveryPreservesWinningEvidenceAndOutbox() throws Exception {
        Fixture fixture = fixture("append-recovery-race");
        long targetId = fixture.targetIds().getFirst();
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        assertTrue(fixture.execution().claim(targetId, "worker", now.minusSeconds(5), now.minusSeconds(1)));
        CyclicBarrier start = new CyclicBarrier(3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> writer = executor.submit(() -> {
                start.await();
                try {
                    fixture.execution().appendOutput(
                            targetId, "worker", 1, OutputStreamType.STDOUT,
                            "recoverable-output", 18, 3, false, now);
                    fixture.execution().updateOutputSnapshot(
                            targetId, "worker", "recoverable-output", "", false);
                    return true;
                } catch (ExecutionLeaseLostException lost) {
                    return false;
                }
            });
            Future<Integer> reaper = executor.submit(() -> {
                start.await();
                return fixture.execution().failRecoverableTargets(
                        now, now.minusSeconds(120), "TRANSIENT_CREDENTIAL_LOST", 10);
            });
            start.await();
            writer.get();
            assertEquals(1, reaper.get());
        }

        assertTerminalEvidenceMatchesEvents(fixture, targetId, "FAILED");
        assertEquals(1, fixture.outboxCount());
        String payload = fixture.onlyOutboxPayload();
        assertTrue(payload.contains("FAILED"));
        assertEquals(fixture.stdout(targetId).contains("recoverable-output"),
                payload.contains("recoverable-output"));
    }

    @Test
    void concurrentTargetsAllocateDistinctCollectionOutputSequences() throws Exception {
        Fixture fixture = fixture("sequence-race");
        List<Long> targetIds = fixture.targetIds();
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        assertTrue(fixture.execution().claim(targetIds.getFirst(), "worker-a", now, now.plusSeconds(60)));
        assertTrue(fixture.execution().claim(targetIds.get(1), "worker-b", now, now.plusSeconds(60)));
        CyclicBarrier start = new CyclicBarrier(3);
        long first;
        long second;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Long> a = executor.submit(() -> {
                start.await();
                return fixture.execution().appendOutput(
                        targetIds.getFirst(), "worker-a", 1, OutputStreamType.STDOUT,
                        "target-a", 8, 1, false, now);
            });
            Future<Long> b = executor.submit(() -> {
                start.await();
                return fixture.execution().appendOutput(
                        targetIds.get(1), "worker-b", 1, OutputStreamType.STDOUT,
                        "target-b", 8, 1, false, now);
            });
            start.await();
            first = a.get();
            second = b.get();
        }

        assertEquals(List.of(1L, 2L), java.util.stream.Stream.of(first, second).sorted().toList());
        assertEquals(List.of(1L, 2L), fixture.jdbc().sql(
                        "select sequence_no from device_ops_collection_output_event where task_id='sequence-race' order by sequence_no")
                .query(Long.class).list());
        assertEquals(2L, fixture.jdbc().sql(
                        "select output_sequence from device_ops_collection where task_id='sequence-race'")
                .query(Long.class).single());
    }

    private static void assertTerminalEvidenceMatchesEvents(Fixture fixture, long targetId, String status) {
        assertEquals(status, fixture.jdbc().sql("select status from device_ops_collection_target where id=:id")
                .param("id", targetId).query(String.class).single());
        String eventOutput = fixture.jdbc().sql(
                        "select content from device_ops_collection_output_event where target_id=:id and stream_type='STDOUT' order by sequence_no")
                .param("id", targetId).query(String.class).list().stream().reduce("", String::concat);
        assertEquals(eventOutput, fixture.stdout(targetId));
    }

    private static Fixture fixture(String taskId) throws Exception {
        org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        CollectionJdbcRepository repository = new CollectionJdbcRepository(
                JdbcClient.create(dataSource), new DataSourceTransactionManager(dataSource));
        repository.saveOrGetExisting(task(taskId));
        JdbcClient jdbc = JdbcClient.create(dataSource);
        JdbcCollectionExecutionPersistencePort execution = new JdbcCollectionExecutionPersistencePort(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper());
        return new Fixture(jdbc, execution, taskId);
    }

    private static CollectionTask task(String taskId) {
        return CollectionTask.submitted(
                taskId, "pms", "project-a", "request-" + taskId, "idem-" + taskId,
                List.of(target("device-a", "10.0.0.1"), target("device-b", "10.0.0.2")),
                ScriptArtifact.external(
                        "inventory", "1", "show version", sha256("show version"),
                        ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null),
                "INSPECTION", URI.create("https://callback.example/events"));
    }

    private static CollectionTarget target(String device, String host) {
        return CollectionTarget.forSnapshot(
                CollectionContextSnapshot.of(
                        "pms", "project-a", "Project A", "PA",
                        device, device, "vendor", "model", Map.of()),
                host, 22, "operator", null);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(
            JdbcClient jdbc,
            JdbcCollectionExecutionPersistencePort execution,
            String taskId) {
        private List<Long> targetIds() {
            return jdbc.sql("select id from device_ops_collection_target where task_id=:task order by id")
                    .param("task", taskId).query(Long.class).list();
        }
        private int outboxCount() {
            return jdbc.sql("select count(*) from device_ops_outbox where aggregate_id=:task")
                    .param("task", taskId).query(Integer.class).single();
        }
        private String onlyOutboxPayload() {
            return jdbc.sql("select payload from device_ops_outbox where aggregate_id=:task")
                    .param("task", taskId).query(String.class).single();
        }
        private String stdout(long targetId) {
            return Optional.ofNullable(jdbc.sql(
                            "select standard_output from device_ops_collection_target where id=:id")
                    .param("id", targetId).query(String.class).single()).orElse("");
        }
    }
}
