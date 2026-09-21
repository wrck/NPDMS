package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.port.ManagementQueryPort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class ManagementMySqlIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("management_test").withUsername("management").withPassword("synthetic-test-only");

    @Test void mysqlV19PaginationBinaryClaimsAndContentProof() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/mysql-migration").target("18").load().migrate();
        var jdbc = JdbcClient.create(source);
        JdbcManagementQueryAdapterTest.insert(jdbc, "legacy", "owned", "p", "LOCAL_MANAGED", "1");
        Flyway.configure().dataSource(source).locations("classpath:db/mysql-migration").target("19").load().migrate();
        Flyway.configure().dataSource(source).locations("classpath:db/mysql-migration").load().migrate();
        assertNull(jdbc.sql("select submission_snapshot_json from device_ops_collection where task_id='legacy'").query((rs,n) -> rs.getString(1)).list().getFirst());
        assertNull(jdbc.sql("select created_at from device_ops_collection where task_id='legacy'").query((rs,n) -> rs.getTimestamp(1)).list().getFirst());
        JdbcManagementQueryAdapterTest.insert(jdbc, "a", "owned", "p", "LOCAL_MANAGED", "1");
        JdbcManagementQueryAdapterTest.insert(jdbc, "b", "owned", "p2", "LOCAL_MANAGED", "1");
        JdbcManagementQueryAdapterTest.insert(jdbc, "secret", "owned", "P", "LOCAL_MANAGED", "1");
        JdbcManagementQueryAdapterTest.insert(jdbc, "foreign", "Owned", "p", "LOCAL_MANAGED", "1");
        jdbc.sql("insert into device_ops_script(namespace,script_key,source) values('owned','script','LOCAL_MANAGED')").update();
        jdbc.sql("insert into device_ops_script_version(script_id,version,content,sha256,parser_type) select id,'1','secret-command',:hash,'NONE' from device_ops_script")
                .param("hash", "a".repeat(64)).update();
        var adapter = new JdbcManagementQueryAdapter(jdbc, true);
        var scope = new ManagementQueryPort.Scope(List.of("owned"), List.of("p", "p2"));
        var evidence = new JdbcCollectionEvidenceQueryAdapter(jdbc, true);
        assertEquals("secret-command", evidence.find(scope, "owned", "p", "a").orElseThrow().input().content());
        assertTrue(evidence.find(scope, "owned", null, "secret").isEmpty());
        assertTrue(evidence.find(scope, "Owned", null, "foreign").isEmpty());
        assertTrue(evidence.find(scope, "owned", null, "A").isEmpty());
        assertTrue(evidence.find(new ManagementQueryPort.Scope(List.of("*"), List.of("p")), "owned", null, "a").isEmpty());
        assertTrue(evidence.find(new ManagementQueryPort.Scope(List.of("*"), List.of("p"), true), "owned", null, "a").isPresent());
        var filter = ManagementQueryPort.Filter.empty();
        assertEquals(3, adapter.collections(scope, filter, 0, 1).total());
        assertEquals(3, adapter.overview(scope, filter).total());
        assertEquals("legacy", adapter.collections(scope, filter, 2, 1).items().getFirst().collectionId());
        assertEquals(1, adapter.scripts(scope, filter, 0, 20).total());
        assertTrue(adapter.content(scope, null, "a").isPresent());
        assertTrue(adapter.content(scope, null, "secret").isEmpty());
        assertTrue(adapter.content(scope, null, "foreign").isEmpty());
        assertTrue(adapter.content(scope, "Owned", "a").isEmpty());
        assertTrue(adapter.content(scope, null, "A").isEmpty());
        assertEquals(0, adapter.collections(new ManagementQueryPort.Scope(List.of("owned"), List.of("P2")), filter, 0, 20).total());
        assertTrue(jdbc.sql("show index from device_ops_collection where Key_name='idx_collection_management_scope'").query().listOfRows().size() > 0);
    }
}
