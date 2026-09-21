package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionContextSnapshot;
import com.dp.deviceops.core.model.CollectionSemanticParsing;
import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CollectionTask;
import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dp.deviceops.adapter.persistence.jdbc.ParserRegistryPersistenceTest.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionParserClosedLoopJdbcTest {

    @Test
    void terminalCollectionCreatesOnePinnedParserTaskWithCommandEvidence() throws Exception {
        DataSource source = ParserRegistryPersistenceTest.migrated("collection-closed-loop");
        JdbcClient jdbc = JdbcClient.create(source);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        TransactionTemplate transactions = new TransactionTemplate(manager);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        JdbcParserReleaseRepository releases = new JdbcParserReleaseRepository(jdbc, transactions, json, clock);
        releases.createLogType(new LogType("show-tech", "Show Tech", "", NOW, NOW));
        releases.saveDraft(ParserRegistryPersistenceTest.draft("release-frozen", 1),
                ParserRegistryPersistenceTest.bundle());
        releases.saveValidation(new ParserReleaseValidation("release-frozen", 1, true, 1, List.of(), NOW));
        assertTrue(releases.publish("release-frozen", ReleaseState.DRAFT));

        CollectionJdbcRepository collections = new CollectionJdbcRepository(jdbc, manager);
        collections.saveOrGetExisting(collection("collection-1"));
        long targetId = collections.findTargetIds("collection-1").getFirst();

        JdbcParserPayloadStore payloads = new JdbcParserPayloadStore(jdbc, clock,
                () -> "collection-input-" + UUID.randomUUID());
        JdbcParseTaskRepository parserTasks = new JdbcParseTaskRepository(jdbc, transactions, json,
                () -> "result-" + UUID.randomUUID(), () -> "result-payload-" + UUID.randomUUID(),
                () -> "event-" + UUID.randomUUID());
        JdbcCollectionParserTaskAppender appender = new JdbcCollectionParserTaskAppender(
                jdbc, json, payloads, parserTasks, clock);
        JdbcCollectionExecutionPersistencePort execution = new JdbcCollectionExecutionPersistencePort(
                jdbc, transactions, json, clock, appender);

        assertTrue(execution.claim(targetId, "collector-1", NOW, NOW.plusSeconds(60)));
        CommandOutputBlock completed = new CommandOutputBlock(1, "show version", CommandBlockStatus.SUCCEEDED,
                "software version 1.2.3", "", 22, 1, false, 0, null,
                Map.of("legacy", "kept"), List.of(), NOW, NOW.plusSeconds(1), false);
        execution.initializeCommandBlocks(targetId, "collector-1", List.of(
                new CommandOutputBlock(1, "show version", CommandBlockStatus.PENDING,
                        "", "", 0, 0, false, null, null, Map.of(), List.of(), null, null, false)));
        execution.updateCommandBlocks(targetId, "collector-1", List.of(completed));
        execution.updateTarget(targetId, "collector-1", CollectionStatus.SUCCEEDED,
                completed.stdout(), "", 0, null, false, Map.of());

        Map<String, Object> task = jdbc.sql("select * from device_ops_parse_task").query().singleRow();
        assertEquals("release-frozen", task.get("RELEASE_ID"));
        assertEquals("command-output-block/v1", task.get("INPUT_FORMAT"));
        Map<String, Object> context = json.readValue((String) task.get("CONTEXT_JSON"), new TypeReference<>() { });
        assertEquals("collection-1", context.get("collectionId"));
        assertEquals(targetId, ((Number) context.get("targetId")).longValue());
        assertEquals("vendor", context.get("deviceVendor"));
        assertEquals("model", context.get("deviceModel"));

        var linkedResults = new JdbcParseResultQueryAdapter(jdbc, json)
                .listTaskResultsByRequestPrefix("npdp", "collection:collection-1:target:");
        assertEquals(1, linkedResults.size());
        assertEquals("release-frozen", linkedResults.getFirst().task().releaseId());
        assertNull(linkedResults.getFirst().result());

        String payload = jdbc.sql("select content from device_ops_parser_payload where payload_id=:id")
                .param("id", task.get("INPUT_PAYLOAD_ID")).query(String.class).single();
        assertTrue(payload.contains("software version 1.2.3"));
        Map<String, Object> parserInput = json.readValue(payload, new TypeReference<>() { });
        Map<String, Object> snapshot = (Map<String, Object>) parserInput.get("contextSnapshot");
        assertEquals("model", snapshot.get("deviceModel"));

        appender.append(targetId, CollectionStatus.SUCCEEDED);
        assertEquals(1, jdbc.sql("select count(*) from device_ops_parse_task").query(Integer.class).single());
    }

    private static CollectionTask collection(String id) {
        CollectionTarget target = CollectionTarget.forSnapshot(CollectionContextSnapshot.of(
                        "npdp", "project-1", "Project 1", "P1", "device-1", "Device 1",
                        "vendor", "model", Map.of()), "10.0.0.1", 22, "operator", null);
        String command = "show version";
        return CollectionTask.submitted(id, "npdp", "project-1", "external-1", "idem-1",
                List.of(target), ScriptArtifact.adHoc("inline", "1", command, sha256(command), "NONE", null),
                "INSPECTION", null, new CollectionSemanticParsing("show-tech", "release-frozen",
                        "command-output-block/v1", "npdp", URI.create("https://npdp.example/parser-results")));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
