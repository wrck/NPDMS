package com.dp.deviceops.adapter.persistence.jdbc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JdbcManagementQueryAdapterTest {
    @Test void managementAdapterExistsAfterFeatureImplementation() {
        assertDoesNotThrow(() -> Class.forName("com.dp.deviceops.adapter.persistence.jdbc.JdbcManagementQueryAdapter"));
    }

    @Test void creationTimestampMigrationPreservesUnknownLegacyDatesAndDefaultsNewRows() {
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:management-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("18").load().migrate();
        var jdbc = JdbcClient.create(source);
        insert(jdbc, "legacy", "owned", "p", "LOCAL_MANAGED", "1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        assertTrue(jdbc.sql("select column_name from information_schema.columns where table_name='DEVICE_OPS_COLLECTION'")
                .query(String.class).list().contains("CREATED_AT"));
        assertNull(jdbc.sql("select created_at from device_ops_collection where task_id='legacy'").query((rs,n) -> rs.getTimestamp(1)).list().getFirst());
        insert(jdbc, "new", "owned", "p", "LOCAL_MANAGED", "1");
        assertNotNull(jdbc.sql("select created_at from device_ops_collection where task_id='new'").query((rs,n) -> rs.getTimestamp(1)).single());
    }

    @Test void scopesPaginationOverviewAndContentAreDatabaseBound() throws Exception {
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:management-query-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var jdbc = JdbcClient.create(source);
        insert(jdbc, "a", "owned", "p", "LOCAL_MANAGED", "1");
        insert(jdbc, "b", "owned", "p", "LOCAL_MANAGED", "1");
        insert(jdbc, "hidden-project", "owned", "other", "LOCAL_MANAGED", "2");
        insert(jdbc, "hidden-namespace", "foreign", "p", "LOCAL_MANAGED", "1");
        jdbc.sql("update device_ops_collection set created_at=TIMESTAMP '2026-09-08 00:00:00'").update();
        jdbc.sql("insert into device_ops_script(namespace,script_key,source) values('owned','script','LOCAL_MANAGED')").update();
        jdbc.sql("insert into device_ops_script_version(script_id,version,content,sha256,parser_type) select id,'1','secret-command',:hash,'NONE' from device_ops_script")
                .param("hash", "a".repeat(64)).update();
        var type = Class.forName("com.dp.deviceops.adapter.persistence.jdbc.JdbcManagementQueryAdapter");
        var adapter = (com.dp.deviceops.core.port.ManagementQueryPort) type.getConstructor(JdbcClient.class).newInstance(jdbc);
        var scope = new com.dp.deviceops.core.port.ManagementQueryPort.Scope(List.of("owned"), List.of("p"));
        var filter = com.dp.deviceops.core.port.ManagementQueryPort.Filter.empty();
        var page = adapter.collections(scope, filter, 0, 1);
        assertEquals(2, page.total());
        assertEquals("b", page.items().getFirst().collectionId());
        assertEquals("a", adapter.collections(scope, filter, 1, 1).items().getFirst().collectionId());
        assertEquals(2, adapter.overview(scope, filter).total());
        assertNull(page.items().getFirst().status(), "missing targets are unknown, not queued");
        assertEquals(2L, adapter.overview(scope, filter).byStatus().get("UNKNOWN"));
        assertEquals(1, adapter.scripts(scope, filter, 0, 20).total());
        assertEquals("secret-command", adapter.content(scope, null, "a").orElseThrow().content());
        assertTrue(adapter.content(scope, null, "hidden-project").isEmpty());
        assertTrue(adapter.content(scope, null, "hidden-namespace").isEmpty());
        jdbc.sql("update device_ops_collection set script_sha256=:hash where task_id='a'").param("hash", "b".repeat(64)).update();
        assertTrue(adapter.content(scope, null, "a").isEmpty());
        jdbc.sql("update device_ops_collection set script_source='EXTERNAL_DELIVERED' where task_id='b'").update();
        assertTrue(adapter.content(scope, null, "b").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> adapter.collections(scope, filter, 0, 101));
    }

    @Test void everyTwoTargetStatusCombinationMatchesExistingDetailsAndFilters() {
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:management-status-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var jdbc = JdbcClient.create(source);
        insert(jdbc, "status", "owned", "p", "LOCAL_MANAGED", "1");
        for (int i = 0; i < 2; i++) {
            jdbc.sql("insert into device_ops_collection_target(task_id,project_name,project_code,device_key,device_name,vendor,model,extensions_json,host,port,username,status,standard_output,standard_error,output_truncated) values('status','P','P',:device,'D','V','M','{}','synthetic.invalid',22,'test','QUEUED','SECRET-STDOUT','SECRET-STDERR',false)")
                    .param("device", "device-" + i).update();
        }
        var ids = jdbc.sql("select id from device_ops_collection_target order by id").query(Long.class).list();
        var adapter = new JdbcManagementQueryAdapter(jdbc);
        var details = new JdbcCollectionQueryAdapter(jdbc, new com.fasterxml.jackson.databind.ObjectMapper());
        var scope = new com.dp.deviceops.core.port.ManagementQueryPort.Scope(List.of("owned"), List.of("p"));
        for (var first : com.dp.deviceops.core.model.CollectionStatus.values()) {
            for (var second : com.dp.deviceops.core.model.CollectionStatus.values()) {
                jdbc.sql("update device_ops_collection_target set status=:status where id=:id").param("status", first.name()).param("id", ids.get(0)).update();
                jdbc.sql("update device_ops_collection_target set status=:status where id=:id").param("status", second.name()).param("id", ids.get(1)).update();
                var expected = details.find("owned", "p", "status").orElseThrow().status();
                var filter = new com.dp.deviceops.core.port.ManagementQueryPort.Filter(null, null, "device-0", expected, null, null);
                var page = adapter.collections(scope, filter, 0, 20);
                assertEquals(1, page.total(), first + "/" + second);
                assertEquals(expected, page.items().getFirst().status());
                assertEquals(1L, adapter.overview(scope, filter).byStatus().get(expected.name()));
            }
        }
        assertEquals(0, adapter.collections(scope, new com.dp.deviceops.core.port.ManagementQueryPort.Filter(null, null, "absent", null, null, null), 0, 20).total());
        assertEquals(0, adapter.collections(new com.dp.deviceops.core.port.ManagementQueryPort.Scope(List.of("*"), List.of("p")), com.dp.deviceops.core.port.ManagementQueryPort.Filter.empty(), 0, 20).total());
        assertEquals(1, adapter.collections(new com.dp.deviceops.core.port.ManagementQueryPort.Scope(List.of("*"), List.of("p"), true), com.dp.deviceops.core.port.ManagementQueryPort.Filter.empty(), 0, 20).total());
    }

    static void insert(JdbcClient jdbc, String id, String namespace, String project, String source, String version) {
        jdbc.sql("insert into device_ops_collection(task_id,namespace,project_key,idempotency_key,script_source,script_key,script_version,script_content,script_sha256,script_policy,parser_type) values(:id,:ns,:p,:id,:source,'script',:version,'secret-command',:hash,'REGISTER_VERSION','NONE')")
                .param("id", id).param("ns", namespace).param("p", project).param("source", source)
                .param("version", version).param("hash", "a".repeat(64)).update();
    }
}
