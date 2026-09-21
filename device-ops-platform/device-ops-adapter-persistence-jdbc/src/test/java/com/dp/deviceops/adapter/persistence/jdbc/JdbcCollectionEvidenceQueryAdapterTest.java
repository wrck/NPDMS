package com.dp.deviceops.adapter.persistence.jdbc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.dp.deviceops.core.port.ManagementQueryPort;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JdbcCollectionEvidenceQueryAdapterTest {
    @Test void evidenceProjectionExists() {
        assertDoesNotThrow(() -> Class.forName("com.dp.deviceops.adapter.persistence.jdbc.JdbcCollectionEvidenceQueryAdapter"));
    }
    @Test void historicalInputIsTaskOwnedAndDatabaseScoped() throws Exception {
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:evidence-query-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var jdbc = JdbcClient.create(source);
        var type = assertDoesNotThrow(() -> Class.forName("com.dp.deviceops.adapter.persistence.jdbc.JdbcCollectionEvidenceQueryAdapter"));
        var query = (com.dp.deviceops.core.port.CollectionEvidenceQueryPort) type.getConstructor(JdbcClient.class).newInstance(jdbc);
        var scope = new ManagementQueryPort.Scope(List.of("owned"), List.of("p"));
        for (String scriptSource : List.of("LOCAL_MANAGED", "ADHOC_INLINE", "EXTERNAL_DELIVERED")) {
            JdbcManagementQueryAdapterTest.insert(jdbc, scriptSource, "owned", "p", scriptSource, "1");
            jdbc.sql("update device_ops_collection set script_policy='EXECUTION_ONLY',created_at=null where task_id=:id").param("id", scriptSource).update();
            var evidence = query.find(scope, "owned", null, scriptSource).orElseThrow();
            assertEquals("secret-command", evidence.input().content());
            assertEquals("AVAILABLE", evidence.input().contentStatus());
            assertNull(evidence.metadata().createdAt());
            assertEquals("RECONSTRUCTED_FACTS", evidence.submission().provenance());
            assertNull(evidence.submission().snapshot());
            assertTrue(query.find(new ManagementQueryPort.Scope(List.of("OWNED"), List.of("p")), "owned", null, scriptSource).isEmpty());
            assertTrue(query.find(new ManagementQueryPort.Scope(List.of("owned"), List.of("P")), "owned", null, scriptSource).isEmpty());
            assertTrue(query.find(new ManagementQueryPort.Scope(List.of("*"), List.of("p")), "owned", null, scriptSource).isEmpty());
            assertTrue(query.find(new ManagementQueryPort.Scope(List.of("*"), List.of("p"), true), "owned", "p", scriptSource).isPresent());
            assertTrue(query.find(scope, "owned", "other", scriptSource).isEmpty());
            assertTrue(query.find(scope, "OWNED", null, scriptSource).isEmpty());
            assertTrue(query.find(scope, "owned", null, scriptSource.toLowerCase()).isEmpty());
        }
    }
    @Test void v19UpgradePreservesLegacyAndAddsNullableSnapshot() {
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:evidence-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("19").load().migrate();
        var jdbc = JdbcClient.create(source);
        JdbcManagementQueryAdapterTest.insert(jdbc, "legacy", "owned", "p", "LOCAL_MANAGED", "1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        assertTrue(jdbc.sql("select column_name from information_schema.columns where table_name='DEVICE_OPS_COLLECTION'")
                .query(String.class).list().contains("SUBMISSION_SNAPSHOT_JSON"));
    }
}
