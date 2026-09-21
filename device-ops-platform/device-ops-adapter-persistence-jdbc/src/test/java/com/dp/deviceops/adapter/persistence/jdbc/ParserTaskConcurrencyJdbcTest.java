package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.ClaimedTask;
import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
import com.dp.deviceops.parser.semantic.SemanticParseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.dp.deviceops.adapter.persistence.jdbc.ParserRegistryPersistenceTest.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserTaskConcurrencyJdbcTest {

    @Test
    void oneClaimWinsAndLeaseGenerationFencesResultAndOutbox() throws Exception {
        DataSource dataSource = ParserRegistryPersistenceTest.migrated("tasks");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        JdbcParserReleaseRepository releases = ParserRegistryPersistenceTest.repository(dataSource);
        releases.createLogType(new LogType("show-tech", "Show Tech", "", NOW, NOW));
        releases.saveDraft(ParserRegistryPersistenceTest.draft("release-1", 1),
                ParserRegistryPersistenceTest.bundle());
        releases.saveValidation(new com.dp.deviceops.parser.runtime.model.ParserReleaseValidation(
                "release-1", 1, true, 1, List.of(), NOW));
        releases.publish("release-1", com.dp.deviceops.parser.runtime.model.ReleaseState.DRAFT);
        JdbcParserPayloadStore payloads = new JdbcParserPayloadStore(jdbc,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "input-1");
        String inputRef = payloads.put("application/json",
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        AtomicInteger ids = new AtomicInteger();
        JdbcParseTaskRepository first = tasks(dataSource, ids);
        JdbcParseTaskRepository second = tasks(dataSource, ids);
        first.submit(new ParseTaskRepository.SubmitRequest("task-1", "request-1", "npdp", "show-tech",
                "release-1", ParserRegistryPersistenceTest.coordinate("1.0.0"), "command-output-block/v1",
                inputRef, Map.of("projectKey", "P-001"), null, "npdp",
                URI.create("https://npdp.example/results"), NOW));
        Set<WorkerCapability> capabilities = Set.of(new WorkerCapability("1.0.0", null, null));
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<List<ClaimedTask>> left = executor.submit(() -> {
                barrier.await();
                return first.claim("worker-a", capabilities, NOW, NOW.plusSeconds(10), 1);
            });
            Future<List<ClaimedTask>> right = executor.submit(() -> {
                barrier.await();
                return second.claim("worker-b", capabilities, NOW, NOW.plusSeconds(10), 1);
            });
            List<ClaimedTask> winners = new java.util.ArrayList<>();
            winners.addAll(left.get());
            winners.addAll(right.get());
            assertEquals(1, winners.size());
        }
        assertEquals(1, jdbc.sql("select count(*) from device_ops_parse_attempt where task_id='task-1'")
                .query(Integer.class).single());

        assertEquals(1, first.recoverExpired(NOW.plusSeconds(11), 10));
        ClaimedTask current = first.claim("worker-c", capabilities, NOW.plusSeconds(11),
                NOW.plusSeconds(30), 1).getFirst();
        assertEquals(2, current.leaseGeneration());
        ParserRuntimeError stale = assertThrows(ParserRuntimeError.class, () -> first.complete(
                new ParseTaskRepository.Completion("task-1", "worker-a", 1, result(), NOW.plusSeconds(12))));
        assertEquals("PARSER_LEASE_LOST", stale.code());
        assertEquals(0, jdbc.sql("select count(*) from device_ops_parse_result").query(Integer.class).single());
        assertEquals(0, jdbc.sql("select count(*) from device_ops_outbox where event_type='PARSER_RESULT_READY'")
                .query(Integer.class).single());

        first.complete(new ParseTaskRepository.Completion("task-1", "worker-c", 2,
                result(), NOW.plusSeconds(13)));
        assertEquals(1, jdbc.sql("select count(*) from device_ops_parse_result").query(Integer.class).single());
        assertEquals(1, jdbc.sql("select count(*) from device_ops_outbox where event_type='PARSER_RESULT_READY'")
                .query(Integer.class).single());
        JdbcParseResultQueryAdapter queries = new JdbcParseResultQueryAdapter(jdbc, new ObjectMapper());
        String resultId = jdbc.sql("select result_id from device_ops_parse_result").query(String.class).single();
        assertEquals(ParserRegistryPersistenceTest.coordinate("1.0.0"),
                queries.findResult("npdp", resultId).orElseThrow().coordinate());
    }

    @Test
    void heartbeatQueriesAndWaitingReconciliationUseExactCapabilities() {
        DataSource dataSource = ParserRegistryPersistenceTest.migrated("workers");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        JdbcWorkerCapabilityRepository workers = new JdbcWorkerCapabilityRepository(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        workers.heartbeat("worker-1", Set.of(new WorkerCapability("1.0.0", null, null)), NOW.plusSeconds(30));

        assertEquals(1, workers.countAvailable(ParserRegistryPersistenceTest.coordinate("1.0.0"), NOW));
        assertEquals(1, workers.listActive(NOW).size());
        assertEquals(0, workers.countAvailable(new com.dp.deviceops.parser.semantic.ParserCoordinate(
                "show-tech", "1.0.0", "1.0.0", "1.0.0", "1.0.0", "vendor", "1.0.0"), NOW));
    }

    @Test
    void caseInsensitiveDatabaseCollationCannotCrossNamespaceReadBoundaries() throws Exception {
        org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:parser-read-isolation;MODE=MySQL;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1");
        org.flywaydb.core.Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        var releases = ParserRegistryPersistenceTest.repository(dataSource);
        releases.createLogType(new LogType("show-tech", "Show Tech", "", NOW, NOW));
        releases.saveDraft(ParserRegistryPersistenceTest.draft("release-1", 1), ParserRegistryPersistenceTest.bundle());
        new JdbcParserPayloadStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC), () -> "input-1")
                .put("application/json", new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        var tasks = tasks(dataSource, new AtomicInteger());
        tasks.submit(new ParseTaskRepository.SubmitRequest("task-1", "request-1", "Owner", "show-tech",
                "release-1", ParserRegistryPersistenceTest.coordinate("1.0.0"), "command-output-block/v1",
                "input-1", Map.of(), null, null, null, NOW));
        tasks.claim("worker-1", Set.of(new WorkerCapability("1.0.0", null, null)), NOW, NOW.plusSeconds(30), 1);
        tasks.complete(new ParseTaskRepository.Completion("task-1", "worker-1", 1, result(), NOW.plusSeconds(1)));
        String resultId = jdbc.sql("select result_id from device_ops_parse_result").query(String.class).single();
        var queries = new JdbcParseResultQueryAdapter(jdbc, new ObjectMapper());
        org.junit.jupiter.api.Assertions.assertTrue(queries.findTask("Owner", "task-1").isPresent());
        org.junit.jupiter.api.Assertions.assertTrue(queries.findResult("Owner", resultId).isPresent());
        org.junit.jupiter.api.Assertions.assertTrue(queries.findTask("owner", "task-1").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(queries.findResult("owner", resultId).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(queries.listTasks("owner", 10, null).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(queries.listTaskResultsByRequestPrefix("owner", "request-").isEmpty());
    }

    @Test
    void canonicalGenericSectionsRoundTripThroughCompletionAndResultQueries() throws Exception {
        DataSource dataSource = ParserRegistryPersistenceTest.migrated("generic-roundtrip");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        var releases = ParserRegistryPersistenceTest.repository(dataSource);
        releases.createLogType(new LogType("show-tech", "Show Tech", "", NOW, NOW));
        releases.saveDraft(ParserRegistryPersistenceTest.draft("release-1", 1), ParserRegistryPersistenceTest.bundle());
        new JdbcParserPayloadStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC), () -> "input-1")
                .put("application/json", new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        var tasks = tasks(dataSource, new AtomicInteger());
        tasks.submit(new ParseTaskRepository.SubmitRequest("task-1", "request-1", "npdp", "show-tech",
                "release-1", ParserRegistryPersistenceTest.coordinate("1.0.0"), "command-output-block/v1",
                "input-1", Map.of(), null, null, null, NOW));
        tasks.claim("worker-1", Set.of(new WorkerCapability("1.0.0", null, null)), NOW, NOW.plusSeconds(30), 1);
        var entry = new com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry("host", "router", 1, 1, List.of());
        var sections = List.of(
                new com.dp.deviceops.parser.semantic.GenericContent.Section(1, "KEY_VALUE", 1, 1,
                        List.of("host: router"), Map.of("entries", List.of(entry)), List.of()),
                new com.dp.deviceops.parser.semantic.GenericContent.Section(2, "TABLE", 2, 3,
                        List.of("Name State", "eth0 up"), Map.of(
                                "columns", List.of(new com.dp.deviceops.parser.semantic.GenericContent.TableColumn("name", "Name", 0)),
                                "rows", List.of(new com.dp.deviceops.parser.semantic.GenericContent.TableRow(1, Map.of("name", "eth0"))),
                                "custom", Map.of("preserved", true)), List.of()),
                new com.dp.deviceops.parser.semantic.GenericContent.Section(3, "TEXT", 4, 4,
                        List.of("raw"), Map.of(), List.of()));
        var generic = new com.dp.deviceops.parser.semantic.GenericContent(List.of(
                new com.dp.deviceops.parser.semantic.GenericContent.Unit(1, null, null, 0, "show tech", 1, 4,
                        com.dp.deviceops.parser.semantic.GenericContent.StructureStatus.STRUCTURED,
                        sections, List.of(), null)));
        String hash = "0".repeat(64);
        var expected = new SemanticParseResult("1.1.0", "1.0.0", "1.0.0", "1.0.0", hash, hash, hash,
                Map.of(), generic, Map.of(), null, Map.of(), List.of(), List.of());
        tasks.complete(new ParseTaskRepository.Completion("task-1", "worker-1", 1, expected, NOW.plusSeconds(1)));
        String resultId = jdbc.sql("select result_id from device_ops_parse_result").query(String.class).single();
        var queries = new JdbcParseResultQueryAdapter(jdbc, new ObjectMapper());
        var canonical = new com.dp.deviceops.parser.semantic.internal.CanonicalJson();
        var found = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> queries.findResult("npdp", resultId).orElseThrow());
        assertEquals(canonical.text(expected), canonical.text(found.semanticResult()));
        assertEquals(canonical.text(expected), canonical.text(queries.listTaskResultsByRequestPrefix("npdp", "request-")
                .getFirst().result().semanticResult()));
    }

    private static JdbcParseTaskRepository tasks(DataSource dataSource, AtomicInteger ids) {
        return new JdbcParseTaskRepository(JdbcClient.create(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper(),
                () -> "result-" + ids.incrementAndGet(), () -> "output-" + ids.incrementAndGet(),
                () -> "event-" + ids.incrementAndGet());
    }

    private static SemanticParseResult result() {
        String hash = "0".repeat(64);
        return new SemanticParseResult("1.0.0", "1.0.0", "1.0.0", "1.0.0",
                hash, hash, hash, Map.of(), Map.of(), Map.of(), List.of());
    }
}
