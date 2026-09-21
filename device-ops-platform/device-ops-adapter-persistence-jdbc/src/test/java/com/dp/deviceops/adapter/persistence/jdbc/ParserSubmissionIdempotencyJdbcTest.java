package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.SubmitOutcome;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.SubmitRequest;
import com.dp.deviceops.parser.runtime.service.ParseTaskService;
import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
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
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.dp.deviceops.adapter.persistence.jdbc.ParserRegistryPersistenceTest.NOW;
import static org.junit.jupiter.api.Assertions.*;

class ParserSubmissionIdempotencyJdbcTest {

    @Test
    void duplicateRequestComparesEverySemanticField() throws Exception {
        Fixture fixture = fixture();
        SubmitRequest original = request("task-1", "npdp", "release-1", "command-output-block/v1",
                "input-1", Map.of("site", Map.of("id", 7)), "source-1", "consumer-1");
        fixture.tasks.submit(original);
        List<SubmitRequest> changes = List.of(
                request("task-2", "npdp", "release-1", original.inputFormat(), "input-1",
                        Map.of("site", Map.of("id", 8)), "source-1", "consumer-1"),
                request("task-2", "npdp", "release-1", "other-format", "input-1",
                        original.contextSnapshot(), "source-1", "consumer-1"),
                request("task-2", "npdp", "release-1", original.inputFormat(), "input-1",
                        original.contextSnapshot(), "source-2", "consumer-1"),
                request("task-2", "npdp", "release-1", original.inputFormat(), "input-1",
                        original.contextSnapshot(), "source-1", "consumer-2"),
                request("task-2", "npdp", "release-2", original.inputFormat(), "input-1",
                        original.contextSnapshot(), "source-1", "consumer-1"));
        for (SubmitRequest changed : changes) {
            assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(ParserRuntimeError.class,
                    () -> fixture.tasks.submit(changed)).code(), changed.toString());
        }
        assertEquals(1, count(fixture, "device_ops_parse_task"));
    }

    @Test
    void reorderedNestedContextReplaysAndNamespacesAreIndependent() throws Exception {
        Fixture fixture = fixture();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("z", List.of(1, 2));
        nested.put("a", "value");
        SubmitOutcome first = fixture.tasks.submit(request("task-1", "npdp", "release-1",
                "command-output-block/v1", "input-1", Map.of("nested", nested), null, null));
        SubmitRequest repeated = request("task-2", "npdp", "release-1", "command-output-block/v1",
                "input-1", Map.of("nested", Map.of("a", "value", "z", List.of(1L, 2L))), null, null);
        assertEquals(first, fixture.tasks.submit(repeated));
        SubmitOutcome other = fixture.tasks.submit(request("task-3", "other", "release-1",
                repeated.inputFormat(), "input-1", repeated.contextSnapshot(), null, null));
        assertNotEquals(first.taskId(), other.taskId());
        assertEquals(2, count(fixture, "device_ops_parse_task"));
    }

    @Test
    void serviceReplaysAfterActiveSwitchAndRemovalUsingPersistedRequest() throws Exception {
        Fixture fixture = fixture();
        fixture.releases.activate("show-tech", "release-1", null);
        var command = new ParseTaskService.SubmitCommand("request-1", "npdp", "show-tech", null,
                "command-output-block/v1", "input-1", Map.of("project", "one"), null, null);
        SubmitOutcome first = service(fixture).submit(command);
        fixture.releases.activate("show-tech", "release-2", "release-1");
        assertEquals(first, service(fixture).submit(command));
        fixture.releases.clearActive("show-tech", "release-2");
        fixture.releases.disable("release-1");
        assertEquals(first, service(fixture).submit(command));
        assertEquals(1, count(fixture, "device_ops_parse_task"));
    }

    @Test
    void concurrentDifferentContextsHaveOneWinnerAndOneConflict() throws Exception {
        Fixture fixture = fixture();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> left = executor.submit(() -> submitAtBarrier(fixture.tasks,
                    request("task-1", "npdp", "release-1", "command-output-block/v1", "input-1",
                            Map.of("project", "one"), null, null), barrier));
            Future<String> right = executor.submit(() -> submitAtBarrier(tasks(fixture.dataSource),
                    request("task-2", "npdp", "release-1", "command-output-block/v1", "input-1",
                            Map.of("project", "two"), null, null), barrier));
            assertEquals(java.util.Set.of("QUEUED", "IDEMPOTENCY_CONFLICT"),
                    new java.util.HashSet<>(List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS))));
        }
        assertEquals(1, count(fixture, "device_ops_parse_task"));
    }

    @Test
    void concurrentAutoReleaseSubmissionsReplayWinnerDespiteDifferentResolvedPins() throws Exception {
        Fixture fixture = fixture();
        CyclicBarrier barrier = new CyclicBarrier(2);
        SubmitRequest leftRequest = automatic(request("task-1", "npdp", "release-1", "command-output-block/v1",
                "input-1", Map.of("site", "one"), null, null));
        SubmitRequest rightRequest = automatic(request("task-2", "npdp", "release-2", "command-output-block/v1",
                "input-1", Map.of("site", "one"), null, null));
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<SubmitOutcome> left = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return fixture.tasks.submit(leftRequest);
            });
            Future<SubmitOutcome> right = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return tasks(fixture.dataSource).submit(rightRequest);
            });
            assertEquals(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, count(fixture, "device_ops_parse_task"));
    }

    @Test
    void explicitAndAutomaticReleaseIntentCannotBeChangedOnRetry() throws Exception {
        Fixture fixture = fixture();
        SubmitRequest explicit = request("task-1", "npdp", "release-1", "command-output-block/v1",
                "input-1", Map.of(), null, null);
        fixture.tasks.submit(explicit);
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(ParserRuntimeError.class,
                () -> fixture.tasks.submit(automatic(explicit))).code());
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(ParserRuntimeError.class,
                () -> fixture.tasks.findSubmission(automatic(explicit).identity())).code());
    }

    @Test
    void legacyRowsReplayWithoutActiveReleaseButStillCompareKnownSemantics() throws Exception {
        Fixture fixture = fixture();
        SubmitRequest original = request("task-1", "npdp", "release-1", "command-output-block/v1",
                "input-1", Map.of("site", "one"), null, null);
        SubmitOutcome first = fixture.tasks.submit(original);
        fixture.jdbc.sql("update device_ops_parse_task set requested_release_id=null,requested_release_recorded=false")
                .update();
        assertEquals(first, fixture.tasks.findSubmission(automatic(original).identity()).orElseThrow());
        assertEquals(first, fixture.tasks.findSubmission(original.identity()).orElseThrow());
        SubmitRequest changed = request("task-2", "npdp", "release-2", original.inputFormat(),
                "input-1", original.contextSnapshot(), null, null);
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(ParserRuntimeError.class,
                () -> fixture.tasks.findSubmission(changed.identity())).code());
    }

    @Test
    void existingTaskOwnershipAllowsOnlyItsExactNamespace() throws Exception {
        Fixture fixture = fixture();
        JdbcParserPayloadStore payloads = new JdbcParserPayloadStore(fixture.jdbc,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "unused");
        assertFalse(payloads.isOwnedBy("npdp", "input-1"));
        fixture.tasks.submit(request("task-1", "npdp", "release-1", "command-output-block/v1",
                "input-1", Map.of(), null, null));
        assertTrue(payloads.isOwnedBy("npdp", "input-1"));
        assertFalse(payloads.isOwnedBy("other", "input-1"));
        assertFalse(payloads.isOwnedBy("NPDP", "input-1"));
    }

    @Test
    void taskIdCollisionWithoutMatchingRequestDoesNotReplayAnotherTask() throws Exception {
        Fixture fixture = fixture();
        fixture.tasks.submit(request("task-1", "npdp", "release-1", "command-output-block/v1",
                "input-1", Map.of(), null, null));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> fixture.tasks.submit(request("task-1", "other", "release-1", "command-output-block/v1",
                        "input-1", Map.of(), null, null)));
    }

    @Test
    void concurrentSubmissionsInsideRepeatableReadTransactionsReplayTheCommittedWinner() throws Exception {
        Fixture fixture = fixture();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<SubmitOutcome> left = executor.submit(() -> submitInTransaction(fixture, "task-1", barrier));
            Future<SubmitOutcome> right = executor.submit(() -> submitInTransaction(fixture, "task-2", barrier));
            assertEquals(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, count(fixture, "device_ops_parse_task"));
    }

    private static SubmitOutcome submitInTransaction(Fixture fixture, String id, CyclicBarrier barrier) {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(fixture.dataSource));
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> {
            fixture.jdbc.sql("select count(*) from device_ops_parse_task").query(Integer.class).single();
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
            return tasks(fixture.dataSource).submit(request(id, "npdp", "release-1", "command-output-block/v1",
                    "input-1", Map.of(), null, null));
        });
    }

    private static SubmitRequest automatic(SubmitRequest request) {
        return new SubmitRequest(request.taskId(), request.requestId(), request.callerNamespace(), request.logType(),
                request.releaseId(), request.coordinate(), request.inputFormat(), request.inputRef(),
                request.contextSnapshot(), request.sourceResultId(), request.resultConsumerId(),
                request.resultDestination(), request.createdAt(), null);
    }

    private static String submitAtBarrier(JdbcParseTaskRepository tasks, SubmitRequest request,
            CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            return tasks.submit(request).state().name();
        } catch (ParserRuntimeError error) {
            return error.code();
        }
    }

    private static ParseTaskService service(Fixture fixture) {
        return new ParseTaskService(fixture.releases, tasks(fixture.dataSource),
                new JdbcParseResultQueryAdapter(fixture.jdbc, new ObjectMapper()), id -> Optional.empty(),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> UUID.randomUUID().toString());
    }

    private static SubmitRequest request(String taskId, String namespace, String releaseId, String format,
            String input, Map<String, Object> context, String source, String consumer) {
        return new SubmitRequest(taskId, "request-1", namespace, "show-tech", releaseId,
                ParserRegistryPersistenceTest.coordinate("release-2".equals(releaseId) ? "2.0.0" : "1.0.0"),
                format, input, context, source, consumer,
                consumer == null ? null : URI.create("https://example.test/results"), NOW);
    }

    private static int count(Fixture fixture, String table) {
        return fixture.jdbc.sql("select count(*) from " + table).query(Integer.class).single();
    }

    private static Fixture fixture() throws Exception {
        DataSource dataSource = ParserRegistryPersistenceTest.migrated("submission-" + UUID.randomUUID());
        JdbcClient jdbc = JdbcClient.create(dataSource);
        JdbcParserReleaseRepository releases = ParserRegistryPersistenceTest.repository(dataSource);
        releases.createLogType(new LogType("show-tech", "Show Tech", "", NOW, NOW));
        for (int version = 1; version <= 2; version++) {
            String id = "release-" + version;
            releases.saveDraft(ParserRegistryPersistenceTest.draft(id, version + ".0.0", 1),
                    ParserRegistryPersistenceTest.bundle(version + ".0.0"));
            releases.saveValidation(new ParserReleaseValidation(id, 1, true, 1, List.of(), NOW));
            releases.publish(id, ReleaseState.DRAFT);
        }
        new JdbcParserPayloadStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC), () -> "input-1")
                .put("application/json", new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        return new Fixture(dataSource, jdbc, releases, tasks(dataSource));
    }

    private static JdbcParseTaskRepository tasks(DataSource dataSource) {
        return new JdbcParseTaskRepository(JdbcClient.create(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper(),
                () -> UUID.randomUUID().toString(), () -> UUID.randomUUID().toString(),
                () -> UUID.randomUUID().toString());
    }

    private record Fixture(DataSource dataSource, JdbcClient jdbc,
            JdbcParserReleaseRepository releases, JdbcParseTaskRepository tasks) { }
}
