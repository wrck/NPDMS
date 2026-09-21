package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionContextSnapshot;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CollectionTask;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.service.CollectionIdempotencyConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class CollectionSubmissionMySqlIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("collection_submission").withUsername("collection").withPassword("collection_test");

    @Test
    void mysqlV18AndDefaultCollationPreserveExactScopeAndConcurrentReplay() throws Exception {
        DataSource source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/mysql-migration").load().migrate();
        JdbcClient jdbc = JdbcClient.create(source);
        CollectionJdbcRepository repository = repository(source);
        repository.saveOrGetExisting(task("original", "scope", "npdp", "project-a", "v1:original"));
        assertEquals("v1:original", repository.findByIdempotencyKey("npdp", "scope").orElseThrow().submissionFingerprint());
        assertEquals("original", repository.saveOrGetExisting(task("repeat", "scope", "npdp", "project-a", "v1:original")).task().taskId());
        assertTrue(repository.findByIdempotencyKey("NPDP", "scope").isEmpty());
        assertTrue(repository.findByIdempotencyKey("npdp", "SCOPE").isEmpty());
        assertThrows(CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(task("namespace", "scope", "NPDP", "project-a", "v1:original")));
        assertThrows(CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(task("project", "scope", "npdp", "Project-a", "v1:original")));
        assertThrows(CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(task("changed", "scope", "npdp", "project-a", "v1:changed")));
        var externalCollision = task("external-collision", "other-key", "npdp", "project-a", "v1:original");
        assertThrows(CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(CollectionTask.submitted(externalCollision.taskId(), "npdp", "project-a",
                        "request-scope", externalCollision.idempotencyKey(), externalCollision.targets(), externalCollision.script(),
                        null, null, null, externalCollision.submissionFingerprint())));

        var details = new JdbcCollectionQueryAdapter(jdbc, new ObjectMapper());
        assertTrue(details.find("npdp", "project-a", "original").isPresent());
        assertTrue(details.find("NPDP", "original").isEmpty());
        assertTrue(details.find("npdp", "Project-a", "original").isEmpty());
        assertTrue(details.find("npdp", "project-a", "ORIGINAL").isEmpty());
        long target = jdbc.sql("select id from device_ops_collection_target where task_id='original'").query(Long.class).single();
        jdbc.sql("insert into device_ops_collection_output_event(task_id,target_id,sequence_no,stream_type,content,received_bytes,page_count,output_truncated,created_at) values('original',:target,1,'STDOUT','private',7,0,false,current_timestamp)")
                .param("target", target).update();
        var events = new JdbcCollectionOutputEventQueryAdapter(jdbc);
        assertEquals(1, events.findAfter("npdp", "project-a", "original", 0, 10).size());
        assertTrue(events.findAfter("NPDP", "project-a", "original", 0, 10).isEmpty());
        assertTrue(events.findAfter("npdp", "Project-a", "original", 0, 10).isEmpty());
        assertTrue(events.findAfter("npdp", null, "ORIGINAL", 0, 10).isEmpty());

        repository.saveOrGetExisting(task("legacy", "legacy-key", "npdp", "project-a", null));
        assertThrows(CollectionIdempotencyConflictException.class,
                () -> repository.saveOrGetExisting(task("legacy-retry", "legacy-key", "npdp", "project-a", "v1:new")));
        assertEquals("legacy", repository.saveOrGetExisting(task("old-caller", "legacy-key", "npdp", "project-b", null)).task().taskId());

        List<String> same = race(source, "same", "v1:same", "v1:same");
        assertEquals(same.getFirst(), same.getLast());
        assertTrue(repository.findByIdempotencyKey("npdp", "same").orElseThrow().submissionSnapshot().contains(same.getFirst()));
        assertTrue(repository.findByIdempotencyKey("npdp", "scope").orElseThrow().submissionSnapshot().contains("original"));
        assertFalse(repository.findByIdempotencyKey("npdp", "scope").orElseThrow().submissionSnapshot().contains("repeat"));
        List<String> different = race(source, "different", "v1:left", "v1:right");
        assertEquals(1, different.stream().filter("CONFLICT"::equals).count());
        assertEquals(4, jdbc.sql("select count(*) from device_ops_collection").query(Integer.class).single());
        assertEquals(4, jdbc.sql("select count(*) from device_ops_collection_target").query(Integer.class).single());
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> {
            repository.saveOrGetExisting(task("rollback", "rollback-key", "npdp", "project-a", "v1:rollback"));
            status.setRollbackOnly();
        });
        assertTrue(repository.findByIdempotencyKey("npdp", "rollback-key").isEmpty());
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> {
            repository.saveOrGetExisting(task("outer-winner", "outer-key", "npdp", "project-a", "same"));
            var replay = repository.saveOrGetExisting(task("outer-replay", "outer-key", "npdp", "project-a", "same"));
            assertFalse(replay.created());
            assertTrue(replay.task().submissionSnapshot().contains("outer-winner"));
            status.setRollbackOnly();
        });
        assertTrue(repository.findByIdempotencyKey("npdp", "outer-key").isEmpty());
    }

    private static List<String> race(DataSource source, String key, String leftFingerprint, String rightFingerprint) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> submitInTransaction(source, key + "-left", key, leftFingerprint, barrier));
            var right = pool.submit(() -> submitInTransaction(source, key + "-right", key, rightFingerprint, barrier));
            return List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS));
        }
    }

    private static String submitInTransaction(DataSource source, String id, String key, String fingerprint, CyclicBarrier barrier) {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try {
            return transaction.execute(status -> {
                assertTrue(repository(source).findByIdempotencyKey("npdp", key).isEmpty());
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
                return repository(source).saveOrGetExisting(task(id, key, "npdp", "project-a", fingerprint)).task().taskId();
            });
        } catch (CollectionIdempotencyConflictException conflict) {
            return "CONFLICT";
        }
    }

    private static CollectionJdbcRepository repository(DataSource source) {
        return new CollectionJdbcRepository(JdbcClient.create(source), new DataSourceTransactionManager(source));
    }

    private static CollectionTask task(String id, String key, String namespace, String project, String fingerprint) {
        String content = "show clock";
        try {
            return CollectionTask.submitted(id, namespace, project, "request-" + key, key,
                    List.of(CollectionTarget.forSnapshot(CollectionContextSnapshot.of(namespace, project, "Project", "P",
                            "device", "Device", "vendor", "model", Map.of()), "10.0.0.1", 22, "operator", null)),
                    ScriptArtifact.adHoc("inline", "1", content,
                            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))),
                            "NONE", null), null, null, null, fingerprint)
                    .withSubmissionSnapshot("{\"schemaVersion\":1,\"body\":{\"winner\":\"" + id + "\"}}");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
