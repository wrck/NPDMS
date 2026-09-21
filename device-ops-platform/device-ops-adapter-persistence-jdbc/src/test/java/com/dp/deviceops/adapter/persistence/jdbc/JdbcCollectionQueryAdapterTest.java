package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionContextSnapshot;
import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CollectionTask;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.CollectionQueryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcCollectionQueryAdapterTest {

    @Test
    void returnsScopedRedactedEvidenceWithoutScriptContent() throws Exception {
        DataSource dataSource = migrated();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        CollectionJdbcRepository repository =
                new CollectionJdbcRepository(jdbc, new DataSourceTransactionManager(dataSource));
        repository.saveOrGetExisting(CollectionTask.submitted(
                "collection-1", "npdp", "project-a", "request-1", "idem-1",
                List.of(CollectionTarget.forSnapshot(
                        CollectionContextSnapshot.of("npdp", "project-a", "Project A", "PA",
                                "router-01", "Core Router", "Huawei", "NE40E", Map.of("site", "A1")),
                        "10.20.30.40", 22, "operator", "SHA256:fingerprint"
                )),
                ScriptArtifact.external("inventory", "2026.07", "display version", sha256("display version"),
                        ScriptArtifact.PersistencePolicy.EXECUTION_ONLY, "KEY_VALUE", null),
                "CUTOVER", null
        ));
        jdbc.sql("""
                        UPDATE device_ops_collection_target
                        SET status='PARTIAL_SUCCESS', standard_output='hostname=core-01',
                            standard_error='parser warning', exit_code=0, output_truncated=true,
                            parsed_facts_json='{"hostname":"core-01"}', outcome_message='parser partial'
                        WHERE task_id='collection-1'
                        """)
                .update();

        JdbcCollectionQueryAdapter adapter = new JdbcCollectionQueryAdapter(jdbc, new ObjectMapper());
        CollectionQueryPort.CollectionDetails details =
                adapter.find("npdp", "project-a", "collection-1").orElseThrow();

        assertEquals(CollectionStatus.PARTIAL_SUCCESS, details.status());
        assertEquals("EXTERNAL_DELIVERED", details.script().source());
        assertEquals("inventory", details.script().key());
        assertEquals("hostname=core-01", details.targets().getFirst().stdout());
        assertEquals(Map.of("hostname", "core-01"), details.targets().getFirst().parsedFacts());
        assertTrue(details.targets().getFirst().truncated());
        assertTrue(adapter.find("other", "project-a", "collection-1").isEmpty());
        assertTrue(adapter.find("npdp", "project-b", "collection-1").isEmpty());
    }

    @Test
    void returnsUnscopedTelnetEvidenceByNamespaceWithoutInventingContext() throws Exception {
        DataSource dataSource = migrated();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        CommandExecutionPort.TelnetPrompts prompts =
                new CommandExecutionPort.TelnetPrompts("login:$", "password:$", "router>$");
        CollectionTarget target = CollectionTarget.forSnapshot(
                CollectionContextSnapshot.ofOptional(null, null, Map.of()),
                ConnectionProtocol.TELNET, "10.20.30.23", 23, "operator", null, prompts);
        new CollectionJdbcRepository(jdbc, new DataSourceTransactionManager(dataSource))
                .saveOrGetExisting(CollectionTask.submitted(
                        "unscoped-1", "crt", null, "request-u1", "idem-u1", List.of(target),
                        ScriptArtifact.adHoc("inline", "1", "show clock", sha256("show clock"), "NONE", null),
                        null, null));

        JdbcCollectionQueryAdapter adapter = new JdbcCollectionQueryAdapter(jdbc, new ObjectMapper());
        CollectionQueryPort.CollectionDetails details = adapter.find("crt", "unscoped-1").orElseThrow();

        assertNull(details.projectKey());
        assertNull(details.targets().getFirst().contextSnapshot().project());
        assertNull(details.targets().getFirst().contextSnapshot().device());
        assertEquals(ConnectionProtocol.TELNET, details.targets().getFirst().endpointSnapshot().protocol());
        assertEquals(prompts, details.targets().getFirst().endpointSnapshot().telnetPrompts());
        assertTrue(adapter.find("other", "unscoped-1").isEmpty());
        assertTrue(adapter.find("crt", "project-a", "unscoped-1").isEmpty());
        String projection = new ObjectMapper().writeValueAsString(details);
        assertFalse(projection.toLowerCase().contains("credential"));
        assertFalse(projection.toLowerCase().contains("privatekey"));
        assertFalse(projection.toLowerCase().contains("\"secret\""));
    }

    @Test
    void caseInsensitiveDatabaseRequiresExactNamespaceAndProjectForDetailsAndEvents() {
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:collection-query-ci-" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        JdbcClient jdbc = JdbcClient.create(source);
        new CollectionJdbcRepository(jdbc, new DataSourceTransactionManager(source)).saveOrGetExisting(
                CollectionTask.submitted("private-task", "npdp", "project-a", "request", "idem",
                        List.of(CollectionTarget.forSnapshot(CollectionContextSnapshot.of("npdp", "project-a", "Project", "P",
                                "device", "Device", "vendor", "model", Map.of()), "10.0.0.1", 22, "operator", null)),
                        ScriptArtifact.adHoc("inline", "1", "show clock", sha256("show clock"), "NONE", null), null, null));
        long target = jdbc.sql("select id from device_ops_collection_target").query(Long.class).single();
        jdbc.sql("insert into device_ops_collection_output_event(task_id,target_id,sequence_no,stream_type,content,received_bytes,page_count,output_truncated,created_at) values('private-task',:target,1,'STDOUT','private-output',14,0,false,current_timestamp)")
                .param("target", target).update();
        var details = new JdbcCollectionQueryAdapter(jdbc, new ObjectMapper());
        var events = new JdbcCollectionOutputEventQueryAdapter(jdbc);
        assertTrue(details.find("npdp", "project-a", "private-task").isPresent());
        assertEquals(1, events.findAfter("npdp", "project-a", "private-task", 0, 10).size());
        assertTrue(details.find("NPDP", "private-task").isEmpty());
        assertTrue(details.find("NPDP", "project-a", "private-task").isEmpty());
        assertTrue(details.find("npdp", "Project-a", "private-task").isEmpty());
        assertTrue(details.find("npdp", "project-a", "PRIVATE-TASK").isEmpty());
        assertTrue(events.findAfter("NPDP", "project-a", "private-task", 0, 10).isEmpty());
        assertTrue(events.findAfter("npdp", "Project-a", "private-task", 0, 10).isEmpty());
        assertTrue(events.findAfter("npdp", null, "PRIVATE-TASK", 0, 10).isEmpty());
        assertEquals(1, events.findAfter("npdp", null, "private-task", 0, 10).size());
    }

    private static DataSource migrated() throws Exception {
        org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:file:" + Files.createTempDirectory("device-ops-query")
                .resolve("collections").toAbsolutePath() + ";MODE=MySQL");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        return source;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
