package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionContextSnapshot;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CollectionTask;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.CollectionRepository;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectionPersistenceTest {
    @Test void cancellationIsDurableAndRejectsLateSubmissionWithoutCreatingExecution() throws Exception {
        DataSource source = migrated();
        var repo = repository(source);
        assertTrue(repo.cancelBeforeSubmission("pms", "idem-1"));
        assertTrue(repository(source).cancelBeforeSubmission("pms", "idem-1"));
        assertThrows(com.dp.deviceops.core.service.CollectionIdempotencyConflictException.class,
                () -> repo.saveOrGetExisting(fingerprinted("late", "pms", "project-a", "request-1", "idem-1", "v1")));
        assertEquals(0, count(source, "device_ops_collection"));
        assertEquals(0, count(source, "device_ops_collection_target"));
        assertFalse(repo.isCancelledBeforeSubmission("other", "idem-1"));
    }

    @Test void submissionAndCancellationRaceHasExactlyOneWinner() throws Exception {
        DataSource source = migrated();
        var repo = repository(source);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 12; i++) {
                String key = "race-" + i;
                var start = new java.util.concurrent.CountDownLatch(1);
                var submitted = pool.submit(() -> {
                    start.await();
                    try { return repo.saveOrGetExisting(fingerprinted(key, "pms", "project-a", key, key, "v1")).created(); }
                    catch (com.dp.deviceops.core.service.CollectionIdempotencyConflictException cancelled) { return false; }
                });
                var cancelled = pool.submit(() -> { start.await(); return repo.cancelBeforeSubmission("pms", key); });
                start.countDown();
                boolean accepted = submitted.get(10, java.util.concurrent.TimeUnit.SECONDS);
                boolean fenced = cancelled.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertNotEquals(accepted, fenced);
                assertEquals(accepted, repo.findByIdempotencyKey("pms", key).isPresent());
                assertEquals(fenced, repository(source).isCancelledBeforeSubmission("pms", key));
            }
        }
    }

    @Test void preSubmissionCancellationCannotRewriteAnAcceptedTask() throws Exception {
        DataSource source = migrated();
        var repo = repository(source);
        repo.saveOrGetExisting(fingerprinted("accepted", "pms", "project-a", "request-1", "idem-1", "v1"));
        assertFalse(repo.cancelBeforeSubmission("pms", "idem-1"));
        assertFalse(repo.isCancelledBeforeSubmission("pms", "idem-1"));
        assertEquals("accepted", repo.findByIdempotencyKey("pms", "idem-1").orElseThrow().taskId());
    }
    @Test void replayInsideSameOuterTransactionSeesUncommittedWinnerSnapshot() throws Exception {
        DataSource source = migrated();
        var repo = repository(source);
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> {
            var first = fingerprinted("winner", "pms", "project-a", "request-1", "idem-1", "same").withSubmissionSnapshot("winner-snapshot");
            var replay = fingerprinted("replay", "pms", "project-a", "request-1", "idem-1", "same").withSubmissionSnapshot("loser-snapshot");
            repo.saveOrGetExisting(first);
            var result = repo.saveOrGetExisting(replay);
            assertFalse(result.created());
            assertEquals("winner-snapshot", result.task().submissionSnapshot());
            status.setRollbackOnly();
        });
        assertEquals(0, count(source, "device_ops_collection"));
    }
    @Test void snapshotIsAtomicAndReplayNeverOverwritesWinner() throws Exception {
        var attach = assertDoesNotThrow(() -> CollectionTask.class.getMethod("withSubmissionSnapshot", String.class));
        DataSource source = migrated();
        var repo = repository(source);
        var winner = (CollectionTask) attach.invoke(fingerprinted("winner", "pms", "project-a", "request-1", "idem-1", "v1:same"), "{\"schemaVersion\":1,\"body\":{\"marker\":\"winner\"}}");
        var loser = (CollectionTask) attach.invoke(fingerprinted("loser", "pms", "project-a", "request-1", "idem-1", "v1:same"), "{\"schemaVersion\":1,\"body\":{\"marker\":\"loser\"}}");
        repo.saveOrGetExisting(winner);
        assertFalse(repo.saveOrGetExisting(loser).created());
        assertTrue(JdbcClient.create(source).sql("select submission_snapshot_json from device_ops_collection").query(String.class).single().contains("winner"));
        var rollback = (CollectionTask) attach.invoke(fingerprinted("rollback", "pms", "project-a", "request-2", "idem-2", "v1:same"), "{}");
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> {
            repo.saveOrGetExisting(rollback); status.setRollbackOnly();
        });
        assertEquals(1, count(source, "device_ops_collection"));
    }
    @Test
    void persistsSubmissionFingerprintAndRejectsChangedRequestOrProject() throws Exception {
        DataSource source = migrated();
        CollectionJdbcRepository repository = repository(source);
        repository.saveOrGetExisting(fingerprinted("original", "pms", "project-a", "request-1", "idem-1", "v1:one"));
        assertEquals("v1:one", repository.findByIdempotencyKey("pms", "idem-1").orElseThrow().submissionFingerprint());
        var replay = repository.saveOrGetExisting(fingerprinted("replay", "pms", "project-a", "request-1", "idem-1", "v1:one"));
        assertFalse(replay.created());
        assertEquals("original", replay.task().taskId());
        assertThrows(com.dp.deviceops.core.service.CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(fingerprinted("changed", "pms", "project-a", "request-1", "idem-1", "v1:two")));
        assertThrows(com.dp.deviceops.core.service.CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(fingerprinted("project", "pms", "Project-a", "request-1", "idem-1", "v1:one")));
        assertEquals(1, count(source, "device_ops_collection"));
        assertEquals(1, count(source, "device_ops_collection_target"));
    }

    @Test
    void legacyRowsRejectFingerprintedReplayButNullFingerprintKeepsLegacyBehavior() throws Exception {
        DataSource source = migrated();
        CollectionJdbcRepository repository = repository(source);
        repository.saveOrGetExisting(fingerprinted("legacy", "pms", "project-a", "request-1", "idem-1", null));
        assertThrows(com.dp.deviceops.core.service.CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(fingerprinted("new", "pms", "project-a", "request-1", "idem-1", "v1:one")));
        assertEquals("legacy", repository.saveOrGetExisting(
                fingerprinted("old", "pms", "project-b", "request-1", "idem-1", null)).task().taskId());
    }

    @Test
    void externalRequestCollisionWithDifferentKeyIsAConflictWithNoSideEffects() throws Exception {
        DataSource source = migrated();
        CollectionJdbcRepository repository = repository(source);
        repository.saveOrGetExisting(fingerprinted("original", "pms", "project-a", "request-1", "idem-1", "v1:one"));
        var conflict = assertThrows(com.dp.deviceops.core.service.CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(fingerprinted("other", "pms", "project-a", "request-1", "idem-2", "v1:one")));
        assertFalse(conflict.getMessage().contains("original"));
        assertEquals(1, count(source, "device_ops_collection"));
        assertEquals(1, count(source, "device_ops_collection_target"));
        assertEquals(0, count(source, "device_ops_collection_parser_request"));
        assertEquals(0, count(source, "device_ops_script"));
        assertEquals(0, count(source, "device_ops_outbox"));
    }

    @Test
    void caseInsensitiveDatabaseCannotLeakNamespaceOrIdempotencyKey() throws Exception {
        DataSource source = caseInsensitiveSource();
        CollectionJdbcRepository repository = repository(source);
        repository.saveOrGetExisting(fingerprinted("original", "pms", "project-a", "request-1", "idem-1", "v1:one"));
        assertTrue(repository.findByIdempotencyKey("PMS", "idem-1").isEmpty());
        assertTrue(repository.findByIdempotencyKey("pms", "IDEM-1").isEmpty());
        assertThrows(com.dp.deviceops.core.service.CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(fingerprinted("other", "PMS", "project-a", "request-1", "idem-1", "v1:one")));
    }

    @Test
    void concurrentSameFingerprintsReplayOneWinnerInsideRepeatableRead() throws Exception {
        DataSource source = migrated();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> submitInRepeatableRead(source, "left", "v1:same", barrier));
            var right = pool.submit(() -> submitInRepeatableRead(source, "right", "v1:same", barrier));
            assertEquals(left.get(15, java.util.concurrent.TimeUnit.SECONDS), right.get(15, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, count(source, "device_ops_collection"));
        assertEquals(1, count(source, "device_ops_collection_target"));
    }

    @Test
    void concurrentDifferentFingerprintsYieldOneConflictInsideRepeatableRead() throws Exception {
        DataSource source = migrated();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> submitInRepeatableRead(source, "left", "v1:left", barrier));
            var right = pool.submit(() -> submitInRepeatableRead(source, "right", "v1:right", barrier));
            var results = List.of(left.get(15, java.util.concurrent.TimeUnit.SECONDS), right.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("CONFLICT"::equals).count());
            assertEquals(1, results.stream().filter(value -> value.equals("left") || value.equals("right")).count());
        }
        assertEquals(1, count(source, "device_ops_collection"));
        assertEquals(1, count(source, "device_ops_collection_target"));
    }

    @Test
    void outerRollbackRemovesFingerprintAndAllSubmissionSideEffects() throws Exception {
        DataSource source = migrated();
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> {
            repository(source).saveOrGetExisting(fingerprinted("rolled-back", "pms", "project-a", "request-1", "idem-1", "v1:one"));
            status.setRollbackOnly();
        });
        assertEquals(0, count(source, "device_ops_collection"));
        assertEquals(0, count(source, "device_ops_collection_target"));
        assertEquals(0, count(source, "device_ops_script"));
    }

    @Test
    void additiveV18MigrationExistsForBothDialectsAndKeepsLegacyFingerprintNull() throws Exception {
        String name = "V18__record_collection_submission_fingerprint.sql";
        for (String directory : List.of("db/migration/", "db/mysql-migration/")) {
            try (var migration = getClass().getClassLoader().getResourceAsStream(directory + name)) {
                assertNotNull(migration, directory + name + " is required");
                String sql = new String(migration.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(sql.toLowerCase().contains("submission_fingerprint"));
                assertFalse(sql.toLowerCase().contains("owner"));
            }
        }
        DataSource source = dataSource();
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("17").load().migrate();
        JdbcClient jdbc = JdbcClient.create(source);
        jdbc.sql("insert into device_ops_collection(task_id,namespace,project_key,external_request_id,idempotency_key,script_source,script_key,script_version,script_content,script_sha256,script_policy,parser_type) values('legacy','pms','project-a','request','idem','ADHOC_INLINE','inline','1','show clock',:sha,'EXECUTION_ONLY','NONE')")
                .param("sha", sha256("show clock")).update();
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        assertNull(jdbc.sql("select submission_fingerprint from device_ops_collection where task_id='legacy'").query().singleRow().get("SUBMISSION_FINGERPRINT"));
    }

    private static String submitInRepeatableRead(DataSource source, String id, String fingerprint,
            java.util.concurrent.CyclicBarrier barrier) {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try {
            return transaction.execute(status -> {
                assertTrue(repository(source).findByIdempotencyKey("pms", "idem-1").isEmpty());
                try { barrier.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
                return repository(source).saveOrGetExisting(fingerprinted(id, "pms", "project-a", "request-1", "idem-1", fingerprint)).task().taskId();
            });
        } catch (com.dp.deviceops.core.service.CollectionIdempotencyConflictException conflict) {
            return "CONFLICT";
        }
    }

    private static CollectionTask fingerprinted(String id, String namespace, String project, String request, String key, String fingerprint) {
        return CollectionTask.submitted(id, namespace, project, request, key,
                List.of(CollectionTarget.forSnapshot(CollectionContextSnapshot.of(namespace, project, "Project", "P",
                        "device", "Device", "vendor", "model", Map.of()), "10.0.0.1", 22, "operator", null)),
                ScriptArtifact.adHoc("inline", "1", "show clock", sha256("show clock"), "NONE", null),
                null, null, null, fingerprint);
    }

    private static int count(DataSource source, String table) {
        return JdbcClient.create(source).sql("select count(*) from " + table).query(Integer.class).single();
    }

    private static DataSource caseInsensitiveSource() {
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:collection-ci-" + java.util.UUID.randomUUID() + ";MODE=MySQL;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        return source;
    }

    @Test
    void savesAndReturnsTheIdempotencyWinnerWithFrozenTargetSnapshots() throws Exception {
        DataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        CollectionJdbcRepository repository = repository(dataSource);
        CollectionTask candidate = task("task-1", "request-1", "idem-1");

        CollectionRepository.SaveResult first = repository.saveOrGetExisting(candidate);
        CollectionRepository.SaveResult repeated = repository.saveOrGetExisting(task("task-2", "request-1", "idem-1"));

        assertTrue(first.created());
        assertFalse(repeated.created());
        assertEquals("task-1", repeated.task().taskId());
        assertEquals(2, repeated.task().targets().size());
        assertEquals("device-a", repeated.task().targets().getFirst().contextSnapshot().device().orElseThrow().deviceKey());
        assertEquals("device-b", repeated.task().targets().get(1).contextSnapshot().device().orElseThrow().deviceKey());
        assertEquals(ConnectionProtocol.SSH2, repeated.task().targets().getFirst().endpointSnapshot().protocol());
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("update device_ops_collection_target set status='PARTIAL_SUCCESS', standard_output='raw', standard_error='warning', exit_code=7, outcome_message='parser failed', output_truncated=true where id=(select min(id) from device_ops_collection_target)").update();
        CollectionTarget restored = repository.findByIdempotencyKey("pms", "idem-1").orElseThrow().targets().getFirst();
        assertEquals(com.dp.deviceops.core.model.CollectionStatus.PARTIAL_SUCCESS, restored.status());
        assertEquals("raw", restored.standardOutput());
        assertEquals("warning", restored.standardError());
        assertEquals(7, restored.exitCode().orElseThrow());
        assertEquals("parser failed", restored.outcomeMessage().orElseThrow());
        assertTrue(restored.outputTruncated());
        assertThrows(IllegalStateException.class, () -> repository.saveOrGetExisting(task("task-3", "request-1", "idem-other")));
        assertEquals(0, JdbcClient.create(dataSource).sql("select count(*) from information_schema.tables where table_schema='PUBLIC' and table_name in ('DEVICE_OPS_PROJECT','DEVICE_OPS_DEVICE')").query(Integer.class).single());
        assertEquals(0, JdbcClient.create(dataSource).sql("select count(*) from information_schema.columns where table_schema='PUBLIC' and table_name in ('DEVICE_OPS_COLLECTION','DEVICE_OPS_COLLECTION_TARGET') and (column_name like '%CREDENTIAL%' or column_name like '%PASSWORD%' or column_name like '%PRIVATE_KEY%' or column_name like '%SECRET%')").query(Integer.class).single());
    }

    @Test
    void persistsUnscopedTelnetSnapshotAsNullContextAndSkipsFakeCallbackPointer() throws Exception {
        DataSource dataSource = migrated();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        CollectionJdbcRepository repository = repository(dataSource);
        CommandExecutionPort.TelnetPrompts prompts =
                new CommandExecutionPort.TelnetPrompts("login:$", "password:$", "router>$");
        CollectionTarget telnet = CollectionTarget.forSnapshot(
                CollectionContextSnapshot.ofOptional(null, null, Map.of()),
                ConnectionProtocol.TELNET, "10.0.0.23", 23, "operator", null, prompts);
        CollectionTask task = CollectionTask.submitted(
                "unscoped", "crt", null, "request-unscoped", "idem-unscoped", List.of(telnet),
                ScriptArtifact.adHoc("inline", "1", "show clock", sha256("show clock"), "NONE", null),
                null, null);

        repository.saveOrGetExisting(task);
        CollectionTask restored = repository.findByIdempotencyKey("crt", "idem-unscoped").orElseThrow();

        assertTrue(restored.projectKey().isEmpty());
        assertTrue(restored.targets().getFirst().contextSnapshot().project().isEmpty());
        assertTrue(restored.targets().getFirst().contextSnapshot().device().isEmpty());
        assertEquals(ConnectionProtocol.TELNET, restored.targets().getFirst().endpointSnapshot().protocol());
        assertEquals(prompts, restored.targets().getFirst().endpointSnapshot().telnetPrompts());
        Map<String, Object> persisted = jdbc.sql("""
                select c.project_key, t.project_name, t.project_code, t.device_key, t.device_name,
                       t.vendor, t.model, t.protocol, t.telnet_prompts_json
                from device_ops_collection c
                join device_ops_collection_target t on t.task_id=c.task_id
                where c.task_id='unscoped'
                """).query().singleRow();
        assertNull(persisted.get("PROJECT_KEY"));
        assertNull(persisted.get("PROJECT_NAME"));
        assertNull(persisted.get("PROJECT_CODE"));
        assertNull(persisted.get("DEVICE_KEY"));
        assertNull(persisted.get("DEVICE_NAME"));
        assertNull(persisted.get("VENDOR"));
        assertNull(persisted.get("MODEL"));
        assertEquals("TELNET", persisted.get("PROTOCOL"));
        assertFalse(((String) persisted.get("TELNET_PROMPTS_JSON")).contains("credential"));
        assertEquals(1, jdbc.sql("""
                select count(*) from "flyway_schema_history"
                where "version"='6' and "success"=true
                """).query(Integer.class).single());

        jdbc.sql("update device_ops_collection set callback_uri='https://callback.example/events' where task_id='unscoped'")
                .update();
        long targetId = jdbc.sql("select id from device_ops_collection_target where task_id='unscoped'")
                .query(Long.class).single();
        JdbcCollectionExecutionPersistencePort execution = new JdbcCollectionExecutionPersistencePort(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper());
        assertTrue(execution.claim(targetId, "worker", Instant.now(), Instant.now().plusSeconds(30)));
        execution.updateTarget(targetId, "worker", com.dp.deviceops.core.model.CollectionStatus.FAILED,
                "", "connection failed", null, "AUTH_FAILED", false, Map.of());
        assertEquals(0, jdbc.sql("select count(*) from device_ops_outbox").query(Integer.class).single());
    }

    @Test
    void callbackPayloadContainsScopedPointersButNoCredentialMaterial() throws Exception {
        DataSource dataSource = migrated();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        repository(dataSource).saveOrGetExisting(task("callback", "request-callback", "idem-callback"));
        long targetId = jdbc.sql("select min(id) from device_ops_collection_target where task_id='callback'")
                .query(Long.class).single();
        JdbcCollectionExecutionPersistencePort execution = new JdbcCollectionExecutionPersistencePort(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper());

        assertTrue(execution.claim(targetId, "worker", Instant.now(), Instant.now().plusSeconds(30)));
        execution.updateTarget(targetId, "worker", com.dp.deviceops.core.model.CollectionStatus.SUCCEEDED,
                "ok", "", 0, null, false, Map.of("hostname", "router"));

        String payload = jdbc.sql("select payload from device_ops_outbox where aggregate_id='callback'")
                .query(String.class).single();
        assertTrue(payload.contains("\"projectKey\":\"project-a\""));
        assertTrue(payload.contains("\"deviceKey\":\"device-a\""));
        assertFalse(payload.toLowerCase().contains("credential"));
        assertFalse(payload.toLowerCase().contains("password"));
        assertFalse(payload.toLowerCase().contains("privatekey"));
        assertFalse(payload.toLowerCase().contains("secret"));
    }

    @Test
    void rollsBackCollectionWhenAnyTargetInsertFails() throws Exception {
        DataSource dataSource = migrated();
        CollectionJdbcRepository repository = repository(dataSource);
        CollectionTask broken = CollectionTask.submitted("broken", "pms", "project-a", "request-broken", "idem-broken", List.of(target("device-a", "10.0.0.1"), target("device-b", "x".repeat(501))), ScriptArtifact.external("inventory", "1", "show version", sha256("show version"), ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null), null, null);
        assertThrows(RuntimeException.class, () -> repository.saveOrGetExisting(broken));
        assertTrue(repository.findByIdempotencyKey("pms", "idem-broken").isEmpty());
        assertEquals(0, count(dataSource, "device_ops_collection_target"));
        assertEquals(0, count(dataSource, "device_ops_script"));
        assertEquals(0, count(dataSource, "device_ops_script_version"));
        assertEquals(0, count(dataSource, "device_ops_collection_parser_request"));
        assertEquals(0, count(dataSource, "device_ops_outbox"));
    }

    @Test
    void failedSubmissionSavepointDoesNotPoisonAnEnclosingTransaction() throws Exception {
        DataSource source = migrated();
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> {
            CollectionTask broken = CollectionTask.submitted("broken", "pms", "project-a", "broken-request", "broken-key",
                    List.of(target("device-a", "10.0.0.1"), target("device-b", "x".repeat(501))),
                    ScriptArtifact.external("failed-script", "1", "show version", sha256("show version"),
                            ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null), null, null, null, "v1:broken");
            assertThrows(RuntimeException.class, () -> repository(source).saveOrGetExisting(broken));
            repository(source).saveOrGetExisting(fingerprinted("successful", "pms", "project-a", "request-ok", "key-ok", "v1:ok"));
        });
        assertEquals(1, count(source, "device_ops_collection"));
        assertEquals(1, count(source, "device_ops_collection_target"));
        assertEquals(0, count(source, "device_ops_script"));
        assertEquals(0, count(source, "device_ops_script_version"));
        assertTrue(repository(source).findByIdempotencyKey("pms", "broken-key").isEmpty());
        assertEquals("v1:ok", repository(source).findByIdempotencyKey("pms", "key-ok").orElseThrow().submissionFingerprint());
    }

    @Test
    void completesWithOutboxAtomicallyAndKeepsAttemptsAppendOnly() throws Exception {
        DataSource dataSource = migrated();
        CollectionJdbcRepository collections = repository(dataSource);
        collections.saveOrGetExisting(task("task-1", "request-1", "idem-1"));
        JdbcClient jdbc = JdbcClient.create(dataSource);
        long firstTarget = jdbc.sql("select min(id) from device_ops_collection_target").query(Long.class).single();
        long secondTarget = jdbc.sql("select max(id) from device_ops_collection_target").query(Long.class).single();
        OutboxJdbcRepository outbox = new OutboxJdbcRepository(jdbc, new DataSourceTransactionManager(dataSource));
        OutboxJdbcRepository.OutboxEvent event = new OutboxJdbcRepository.OutboxEvent("event-1", "task-1", "TARGET_COMPLETED", "{}", Instant.parse("2026-01-01T00:00:00Z"));

        outbox.completeTargetAndAppendEvent(firstTarget, "SUCCEEDED", event);
        assertEquals("SUCCEEDED", jdbc.sql("select status from device_ops_collection_target where id=:id").param("id", firstTarget).query(String.class).single());
        assertEquals(List.of(event), outbox.findPending(Instant.parse("2026-01-01T00:00:00Z"), 10));
        assertThrows(IllegalStateException.class, () -> outbox.completeTargetAndAppendEvent(secondTarget, "SUCCEEDED", new OutboxJdbcRepository.OutboxEvent("event-1", "task-1", "TARGET_COMPLETED", "different", event.nextAttemptAt())));
        assertEquals("QUEUED", jdbc.sql("select status from device_ops_collection_target where id=:id").param("id", secondTarget).query(String.class).single());

        collections.appendAttempt(firstTarget, new CollectionJdbcRepository.Attempt(1, "EXECUTING", "worker-1", Instant.now(), Instant.now(), null, null));
        assertThrows(IllegalStateException.class, () -> collections.appendAttempt(firstTarget, new CollectionJdbcRepository.Attempt(1, "FAILED", null, null, Instant.now(), Instant.now(), "duplicate")));
        assertEquals(1, collections.findAttempts(firstTarget).size());
    }

    @Test
    void claimKeepsPersistedStatusQueuedUntilProtocolExecutionActuallyStarts() throws Exception {
        DataSource dataSource = migrated();
        CollectionJdbcRepository collections = repository(dataSource);
        collections.saveOrGetExisting(task("claim-queued", "request-claim", "idem-claim"));
        JdbcClient jdbc = JdbcClient.create(dataSource);
        long targetId = jdbc.sql("select min(id) from device_ops_collection_target where task_id='claim-queued'")
                .query(Long.class).single();
        JdbcCollectionExecutionPersistencePort execution = new JdbcCollectionExecutionPersistencePort(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper());
        Instant now = Instant.parse("2026-08-04T00:00:00Z");

        assertTrue(execution.claim(targetId, "waiting-worker", now, now.plusSeconds(60)));

        assertEquals("QUEUED", jdbc.sql("select status from device_ops_collection_target where id=:id")
                .param("id", targetId).query(String.class).single());
        assertEquals("QUEUED", jdbc.sql("select status from device_ops_collection_attempt where target_id=:id")
                .param("id", targetId).query(String.class).single());
        execution.updateTarget(targetId, "waiting-worker", com.dp.deviceops.core.model.CollectionStatus.CONNECTING,
                "", "", null, null, false, Map.of());
        assertEquals("CONNECTING", jdbc.sql("select status from device_ops_collection_target where id=:id")
                .param("id", targetId).query(String.class).single());
        assertEquals("CONNECTING", jdbc.sql("select status from device_ops_collection_attempt where target_id=:id")
                .param("id", targetId).query(String.class).single());
    }

    @Test
    void pendingClaimAndBatchHeartbeatKeepTargetAndAttemptOnOneShortLease() throws Exception {
        DataSource dataSource = migrated();
        repository(dataSource).saveOrGetExisting(task("handoff", "request-handoff", "idem-handoff"));
        JdbcClient jdbc = JdbcClient.create(dataSource);
        long targetId = jdbc.sql("select min(id) from device_ops_collection_target where task_id='handoff'")
                .query(Long.class).single();
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        JdbcCollectionExecutionPersistencePort execution = new JdbcCollectionExecutionPersistencePort(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
        Instant baselineLease = now.plusSeconds(30);
        assertTrue(execution.claimPending(targetId, "worker", now, Duration.ofSeconds(30)));
        assertEquals(List.of(), execution.renewPendingLeases(
                List.of(new CollectionExecutionPersistencePort.ClaimedTarget(targetId, "worker")),
                now.plusSeconds(5)));

        Instant targetLease = jdbc.sql("select lease_until from device_ops_collection_target where id=:id")
                .param("id", targetId)
                .query((resultSet, row) -> resultSet.getTimestamp(1).toInstant()).single();
        Instant attemptLease = jdbc.sql("select lease_until from device_ops_collection_attempt where target_id=:id")
                .param("id", targetId)
                .query((resultSet, row) -> resultSet.getTimestamp(1).toInstant()).single();
        assertEquals(baselineLease, targetLease);
        assertEquals(baselineLease, attemptLease);
        assertEquals(List.of(targetId), execution.renewPendingLeases(
                List.of(new CollectionExecutionPersistencePort.ClaimedTarget(targetId, "stale-worker")),
                now.plusSeconds(60)));
    }

    @Test
    void periodicRecoveryWaitsForLeaseExpiryAndLeavesAnotherLiveOwnerUntouched() throws Exception {
        DataSource dataSource = migrated();
        repository(dataSource).saveOrGetExisting(task("restart-recovery", "request-recovery", "idem-recovery"));
        JdbcClient jdbc = JdbcClient.create(dataSource);
        List<Long> targets = jdbc.sql("select id from device_ops_collection_target where task_id='restart-recovery' order by id")
                .query(Long.class).list();
        JdbcCollectionExecutionPersistencePort execution = new JdbcCollectionExecutionPersistencePort(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper());
        Instant restartAt = Instant.parse("2026-08-04T00:00:00Z");
        assertTrue(execution.claim(targets.getFirst(), "old-instance", restartAt, restartAt.plusSeconds(2)));
        assertTrue(execution.claim(targets.get(1), "live-instance", restartAt, restartAt.plusSeconds(60)));

        assertEquals(0, execution.failRecoverableTargets(
                restartAt.plusSeconds(1), restartAt.minusSeconds(120), "TRANSIENT_CREDENTIAL_LOST", 10));
        assertEquals(1, execution.failRecoverableTargets(
                restartAt.plusSeconds(3), restartAt.minusSeconds(120), "TRANSIENT_CREDENTIAL_LOST", 10));

        assertEquals("FAILED", jdbc.sql("select status from device_ops_collection_target where id=:id")
                .param("id", targets.getFirst()).query(String.class).single());
        assertEquals("QUEUED", jdbc.sql("select status from device_ops_collection_target where id=:id")
                .param("id", targets.get(1)).query(String.class).single());
        assertEquals("QUEUED", jdbc.sql("select status from device_ops_collection_attempt where target_id=:id")
                .param("id", targets.get(1)).query(String.class).single());
        assertEquals(1, jdbc.sql("select count(*) from device_ops_outbox where aggregate_id='restart-recovery'")
                .query(Integer.class).single());
        String recoveredPayload = jdbc.sql("select payload from device_ops_outbox where aggregate_id='restart-recovery'")
                .query(String.class).single();
        assertTrue(recoveredPayload.contains("FAILED"));
        assertTrue(recoveredPayload.contains("device-a"));
    }

    @Test
    void shutdownCancellationPreservesPersistedEvidenceAndEmitsCancelledCallback() throws Exception {
        DataSource dataSource = migrated();
        repository(dataSource).saveOrGetExisting(task("cancel-evidence", "request-cancel", "idem-cancel"));
        JdbcClient jdbc = JdbcClient.create(dataSource);
        long targetId = jdbc.sql("select min(id) from device_ops_collection_target where task_id='cancel-evidence'")
                .query(Long.class).single();
        JdbcCollectionExecutionPersistencePort execution = new JdbcCollectionExecutionPersistencePort(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper());
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        assertTrue(execution.claim(targetId, "shutdown-worker", now, now.plusSeconds(60)));
        execution.updateTarget(targetId, "shutdown-worker", com.dp.deviceops.core.model.CollectionStatus.EXECUTING,
                "partial-output", "partial-error", 7, "running", true, Map.of("fact", "preserved"));

        execution.cancelClaimedTarget(targetId, "shutdown-worker", "SHUTDOWN_CANCELLED");

        Map<String, Object> target = jdbc.sql("select status,standard_output,standard_error,exit_code,output_truncated,parsed_facts_json,outcome_message,lease_owner from device_ops_collection_target where id=:id")
                .param("id", targetId).query().singleRow();
        assertEquals("CANCELLED", target.get("STATUS"));
        assertEquals("partial-output", target.get("STANDARD_OUTPUT"));
        assertEquals("partial-error", target.get("STANDARD_ERROR"));
        assertEquals(7, target.get("EXIT_CODE"));
        assertEquals(true, target.get("OUTPUT_TRUNCATED"));
        assertTrue(target.get("PARSED_FACTS_JSON").toString().contains("preserved"));
        assertEquals("SHUTDOWN_CANCELLED", target.get("OUTCOME_MESSAGE"));
        assertNull(target.get("LEASE_OWNER"));
        String payload = jdbc.sql("select payload from device_ops_outbox where aggregate_id='cancel-evidence'")
                .query(String.class).single();
        assertTrue(payload.contains("CANCELLED"));
        assertTrue(payload.contains("partial-output"));
        assertTrue(payload.contains("preserved"));
    }

    @Test
    void restoresAdHocArtifactAsExecutionOnly() throws Exception {
        DataSource dataSource = migrated();
        CollectionJdbcRepository repository = repository(dataSource);
        CollectionTask task = CollectionTask.submitted("ad-hoc", "pms", "project-a", "request-ad-hoc", "idem-ad-hoc", List.of(target("device-a", "10.0.0.1")), ScriptArtifact.adHoc("inline", "9", "show clock", sha256("show clock"), "NONE", "strict=true"), null, null);
        repository.saveOrGetExisting(task);
        ScriptArtifact restored = repository.findByIdempotencyKey("pms", "idem-ad-hoc").orElseThrow().script();
        assertEquals(ScriptArtifact.ScriptSource.ADHOC_INLINE, restored.source());
        assertEquals(ScriptArtifact.PersistencePolicy.EXECUTION_ONLY, restored.persistencePolicy());
        assertEquals("inline", restored.key());
        assertEquals("9", restored.version());
        assertEquals("show clock", restored.content());
        assertEquals("strict=true", restored.parserConfig());
    }

    private static DataSource dataSource() throws Exception {
        org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:file:" + Files.createTempDirectory("device-ops-h2").resolve("collections").toAbsolutePath() + ";MODE=MySQL");
        return source;
    }

    private static DataSource migrated() throws Exception { DataSource dataSource = dataSource(); Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate(); return dataSource; }
    private static CollectionJdbcRepository repository(DataSource dataSource) { return new CollectionJdbcRepository(JdbcClient.create(dataSource), new DataSourceTransactionManager(dataSource)); }

    private static CollectionTask task(String taskId, String request, String idempotency) {
        return CollectionTask.submitted(taskId, "pms", "project-a", request, idempotency,
                List.of(target("device-a", "10.0.0.1"), target("device-b", "10.0.0.2")),
                ScriptArtifact.external("inventory", "1", "show version", sha256("show version"),
                        ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null), "INSPECTION", URI.create("https://callback.example/events"));
    }

    private static CollectionTarget target(String device, String host) {
        return CollectionTarget.forSnapshot(CollectionContextSnapshot.of("pms", "project-a", "Project A", "PA", device,
                device, "vendor", "model", Map.of("site", "A1")), host, 22, "operator", null);
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes())); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
