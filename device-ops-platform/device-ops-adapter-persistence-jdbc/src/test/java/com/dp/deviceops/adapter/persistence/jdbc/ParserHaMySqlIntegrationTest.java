package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.adapter.web.parser.concurrency.ParserWorkerCoordinator;
import com.dp.deviceops.adapter.web.parser.concurrency.ParserWorkerProperties;
import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.service.ParserPlanCache;
import com.dp.deviceops.parser.runtime.service.ParserTaskExecutor;
import com.dp.deviceops.parser.semantic.DefaultDynamicSemanticParser;
import com.dp.deviceops.parser.semantic.DynamicSemanticParser;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class ParserHaMySqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final Set<WorkerCapability> CAPABILITIES = Set.of(
            new WorkerCapability(ParserPlanCompiler.LEGACY_ENGINE_VERSION, null, null),
            new WorkerCapability(ParserPlanCompiler.ENGINE_VERSION, null, null));

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("device_ops").withUsername("device_ops").withPassword("device_ops_test");

    @Test
    void mysqlSchemaAndTwoWorkersPreservePinnedReleasesAcrossFailover() throws Exception {
        org.springframework.jdbc.datasource.DriverManagerDataSource source =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/mysql-migration").load().migrate();
        JdbcClient jdbc = JdbcClient.create(source);
        assertSchemaParity(jdbc);

        MutableClock clock = new MutableClock(NOW);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        JdbcParserReleaseRepository releasesA = new JdbcParserReleaseRepository(jdbc, transactions, json, clock);
        JdbcParserReleaseRepository releasesB = new JdbcParserReleaseRepository(
                JdbcClient.create(source), transactions, json, clock);
        releasesA.createLogType(new LogType("show-tech", "Show Tech", "", NOW, NOW));
        publish(releasesA, "release-1", "1.0.0");
        publish(releasesA, "release-2", "2.0.0");

        AtomicInteger ids = new AtomicInteger();
        JdbcParserPayloadStore payloads = new JdbcParserPayloadStore(jdbc, clock,
                () -> "input-" + ids.incrementAndGet());
        String inputRef = payloads.put("application/json", new ByteArrayInputStream(validInput()));
        JdbcParseTaskRepository tasksA = tasks(source, json, ids);
        JdbcParseTaskRepository tasksB = tasks(source, json, ids);
        submit(tasksA, "task-a", "request-a", "release-1", "1.0.0", inputRef);
        submit(tasksA, "task-b", "request-b", "release-1", "1.0.0", inputRef);
        submit(tasksA, "task-c", "request-c", "release-2", "2.0.0", inputRef);

        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch releaseBlocked = new CountDownLatch(1);
        AtomicInteger parses = new AtomicInteger();
        DynamicSemanticParser blockingParser = (plan, input) -> {
            if (parses.incrementAndGet() == 2) {
                blocked.countDown();
                try {
                    releaseBlocked.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return new DefaultDynamicSemanticParser().parse(plan, input);
        };
        ParserWorkerCoordinator first = coordinator("worker-a", releasesA, payloads, tasksA,
                blockingParser, clock, properties(1, 1), pool(1));
        first.start();
        first.poll();
        awaitCount(jdbc, "device_ops_parse_result", 1);
        first.poll();
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
        first.stop();

        clock.set(NOW.plusSeconds(1));
        assertEquals(1, tasksB.recoverExpired(clock.instant(), 10));
        ParserWorkerCoordinator second = coordinator("worker-b", releasesB, payloads, tasksB,
                new DefaultDynamicSemanticParser(), clock, properties(2, 2), pool(2));
        second.start();
        second.poll();
        awaitCount(jdbc, "device_ops_parse_result", 3);
        second.stop();

        releaseBlocked.countDown();
        awaitActive(first, 0);
        assertEquals(3, count(jdbc, "device_ops_parse_result"));
        assertEquals(3, jdbc.sql("select count(*) from device_ops_outbox where event_type='PARSER_RESULT_READY'")
                .query(Integer.class).single());
        assertEquals("release-1", releaseFor(jdbc, "task-b"));
        assertEquals("release-2", releaseFor(jdbc, "task-c"));
        assertEquals(resultPayload(jdbc, "task-a"), resultPayload(jdbc, "task-b"));
        assertEquals(1, jdbc.sql("select count(*) from device_ops_parse_attempt where task_id='task-b' "
                        + "and lease_generation=2 and status='SUCCEEDED'").query(Integer.class).single());
    }

    private static void assertSchemaParity(JdbcClient jdbc) {
        Set<String> expectedTables = Set.of(
                "device_ops_script", "device_ops_script_version", "device_ops_collection",
                "device_ops_collection_target", "device_ops_collection_attempt", "device_ops_outbox",
                "device_ops_collection_schedule", "device_ops_collection_output_event",
                "device_ops_credential", "device_ops_saved_connection", "device_ops_collection_command_output",
                "device_ops_parser_log_type", "device_ops_parser_release", "device_ops_parser_active_release",
                "device_ops_parser_payload", "device_ops_parse_task", "device_ops_parse_attempt",
                "device_ops_parse_result", "device_ops_parser_worker", "device_ops_parser_worker_capability",
                "device_ops_collection_parser_request");
        Set<String> actual = Set.copyOf(jdbc.sql("select lower(table_name) from information_schema.tables "
                        + "where table_schema=database() and table_name like 'device_ops_%'")
                .query(String.class).list());
        assertEquals(expectedTables, actual);

        Set<String> requiredFinalColumns = Set.of(
                "device_ops_collection.output_sequence", "device_ops_collection_target.protocol",
                "device_ops_collection_target.telnet_prompts_json", "device_ops_collection_target.queued_at",
                "device_ops_collection_target.parsed_facts_json", "device_ops_collection_output_event.command_index",
                "device_ops_credential.credential_scope", "device_ops_saved_connection.credential_id",
                "device_ops_collection_command_output.command_index", "device_ops_parse_task.lease_generation",
                "device_ops_parse_task.release_id", "device_ops_parse_result.structured_output_payload_id",
                "device_ops_parser_worker.heartbeat_expires_at",
                "device_ops_collection_parser_request.result_destination");
        Set<String> actualColumns = Set.copyOf(jdbc.sql("select concat(lower(table_name),'.',lower(column_name)) "
                        + "from information_schema.columns where table_schema=database()")
                .query(String.class).list());
        assertTrue(actualColumns.containsAll(requiredFinalColumns));
    }

    private static void publish(JdbcParserReleaseRepository releases, String id, String version) {
        releases.saveDraft(ParserRegistryPersistenceTest.draft(id, version, 1),
                ParserRegistryPersistenceTest.bundle(version));
        releases.saveValidation(new ParserReleaseValidation(id, 1, true, 1, List.of(), NOW));
        assertTrue(releases.publish(id, ReleaseState.DRAFT));
    }

    private static JdbcParseTaskRepository tasks(javax.sql.DataSource source, ObjectMapper json, AtomicInteger ids) {
        return new JdbcParseTaskRepository(JdbcClient.create(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)), json,
                () -> "result-" + ids.incrementAndGet(), () -> "output-" + ids.incrementAndGet(),
                () -> "event-" + ids.incrementAndGet());
    }

    private static void submit(JdbcParseTaskRepository tasks, String taskId, String requestId,
            String releaseId, String version, String inputRef) {
        tasks.submit(new ParseTaskRepository.SubmitRequest(taskId, requestId, "npdp", "show-tech",
                releaseId, ParserRegistryPersistenceTest.coordinate(version), "command-output-block/v1",
                inputRef, Map.of("source", "ha-test"), null, "npdp",
                java.net.URI.create("https://npdp.example/results"), NOW));
    }

    private static ParserWorkerCoordinator coordinator(String workerId, JdbcParserReleaseRepository releases,
            JdbcParserPayloadStore payloads, JdbcParseTaskRepository tasks, DynamicSemanticParser parser,
            Clock clock, ParserWorkerProperties properties, ThreadPoolExecutor pool) {
        ParserPlanCompiler compiler = new ParserPlanCompiler();
        ParserTaskExecutor executor = new ParserTaskExecutor(workerId, releases, payloads, tasks,
                compiler, parser, new ParserPlanCache(8), clock, Duration.ofMillis(10));
        return new ParserWorkerCoordinator(workerId, CAPABILITIES, tasks, executor, pool, properties, clock);
    }

    private static ParserWorkerProperties properties(int maximum, int batch) {
        ParserWorkerProperties properties = new ParserWorkerProperties();
        properties.setCoreSize(maximum);
        properties.setMaxSize(maximum);
        properties.setQueueCapacity(0);
        properties.setClaimBatchSize(batch);
        properties.setHeartbeatInterval(Duration.ofSeconds(1));
        properties.setLease(Duration.ofSeconds(30));
        properties.setShutdownGrace(Duration.ofMillis(5));
        return properties;
    }

    private static ThreadPoolExecutor pool(int threads) {
        return new ThreadPoolExecutor(threads, threads, 1, TimeUnit.SECONDS, new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static byte[] validInput() {
        return """
                {"schemaVersion":"1.0.0","commandBlocks":[{"commandIndex":1,
                "commandText":"show version","status":"SUCCEEDED","stdout":"version 1.2.3","stderr":"",
                "receivedBytes":13,"pageCount":1,"truncated":false,"exitCode":0}]}
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static void awaitCount(JdbcClient jdbc, String table, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && count(jdbc, table) != expected) Thread.sleep(20);
        assertEquals(expected, count(jdbc, table));
    }

    private static void awaitActive(ParserWorkerCoordinator coordinator, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && coordinator.activeCount() != expected) Thread.sleep(10);
        assertEquals(expected, coordinator.activeCount());
    }

    private static int count(JdbcClient jdbc, String table) {
        return jdbc.sql("select count(*) from " + table).query(Integer.class).single();
    }

    private static String releaseFor(JdbcClient jdbc, String taskId) {
        return jdbc.sql("select release_id from device_ops_parse_result where task_id=:task")
                .param("task", taskId).query(String.class).single();
    }

    private static String resultPayload(JdbcClient jdbc, String taskId) {
        return jdbc.sql("select p.content from device_ops_parse_result r join device_ops_parser_payload p "
                        + "on p.payload_id=r.structured_output_payload_id where r.task_id=:task")
                .param("task", taskId).query(String.class).single();
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private MutableClock(Instant initial) { current = new AtomicReference<>(initial); }
        private void set(Instant value) { current.set(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current.get(); }
    }
}
