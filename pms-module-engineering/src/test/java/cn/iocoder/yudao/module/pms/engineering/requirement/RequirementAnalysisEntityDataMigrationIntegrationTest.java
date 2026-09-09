package cn.iocoder.yudao.module.pms.engineering.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisEntityDataUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstancePageQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceValueUpdate;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/** All writes use connection-private temporary tables; no application table is changed. */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class RequirementAnalysisEntityDataMigrationIntegrationTest {
    @Test
    void migrationPreservesLegacySourceAndMapperOnlyWritesDraftEntityData() throws Exception {
        assertEquals("npdms_test", System.getenv("NPDMS_DB_NAME"));
        assertEquals("23316", System.getenv("NPDMS_MYSQL_PORT"));
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"), System.getenv("NPDMS_DB_PASSWORD"))) {
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TEMPORARY TABLE sol_preparation (id BIGINT PRIMARY KEY, tenant_id BIGINT, "
                        + "preparation_type_code VARCHAR(64), dynamic_form_instance_id BIGINT, status_code VARCHAR(32), "
                        + "draft_marker INT, version INT, content_version INT, updater VARCHAR(64), update_time DATETIME)");
                sql.execute("CREATE TEMPORARY TABLE plt_dynamic_form_instance (id BIGINT PRIMARY KEY, tenant_id BIGINT, "
                        + "owner_context VARCHAR(64), object_type VARCHAR(64), object_id VARCHAR(64), value_json JSON, "
                        + "deleted BIT DEFAULT b'0', version INT DEFAULT 1, updater VARCHAR(64), update_time DATETIME)");
                sql.execute("INSERT INTO sol_preparation VALUES "
                        + "(101,1,'PRE_04_REQUIREMENT_ANALYSIS',201,'DRAFT',1,3,2,'fixture',NOW()),"
                        + "(102,1,'PRE_04_REQUIREMENT_ANALYSIS',202,'COMPLETED',NULL,4,2,'fixture',NOW()),"
                        + "(103,1,'PRE_04_REQUIREMENT_ANALYSIS',203,'DRAFT',1,1,1,'fixture',NOW()),"
                        + "(104,1,'PRE_02',204,'DRAFT',1,1,1,'fixture',NOW())");
                sql.execute("INSERT INTO plt_dynamic_form_instance (id,tenant_id,owner_context,object_type,object_id,value_json) VALUES "
                        + "(201,1,'SOL','REQUIREMENT_ANALYSIS','101',JSON_OBJECT('name','legacy','enabled',JSON_EXTRACT('false','$'),'count',0,'items',JSON_ARRAY('a','b'))),"
                        + "(202,1,'SOL','REQUIREMENT_ANALYSIS','102',JSON_OBJECT('name','frozen')),"
                        + "(203,2,'SOL','REQUIREMENT_ANALYSIS','103',JSON_OBJECT('name','wrong-tenant')),"
                        + "(204,1,'SOL','REQUIREMENT_ANALYSIS','104',JSON_OBJECT('name','not-pre04')),"
                        + "(205,1,'PLATFORM','MANUAL_DYNAMIC_FORM','205',JSON_OBJECT()),"
                        + "(206,1,'PLATFORM','MANUAL_DYNAMIC_FORM','wrong-key',JSON_OBJECT()),"
                        + "(207,2,'PLATFORM','MANUAL_DYNAMIC_FORM','207',JSON_OBJECT())");
            }
            String source = value(connection, "SELECT value_json FROM plt_dynamic_form_instance WHERE id=201");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../sql/migrations/V210__fsol003_entity_form_values.sql"));
            assertEquals(source, value(connection, "SELECT entity_value_json FROM sol_preparation WHERE id=101"));
            assertEquals("BOOLEAN", value(connection, "SELECT JSON_TYPE(JSON_EXTRACT(entity_value_json,'$.enabled')) FROM sol_preparation WHERE id=101"));
            assertEquals("frozen", value(connection, "SELECT entity_value_json->>'$.name' FROM sol_preparation WHERE id=102"));
            assertNull(value(connection, "SELECT entity_value_json FROM sol_preparation WHERE id=103"));
            assertNull(value(connection, "SELECT entity_value_json FROM sol_preparation WHERE id=104"));
            assertEquals("3", value(connection, "SELECT version FROM sol_preparation WHERE id=101"));

            Configuration configuration = new Configuration(new Environment("entity-migration-test",
                    new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
            try (var mapperXml = getClass().getClassLoader().getResourceAsStream("mapper/preparation/RequirementAnalysisRootMapper.xml")) {
                assertNotNull(mapperXml);
                new XMLMapperBuilder(mapperXml, configuration, "entity-root", configuration.getSqlFragments()).parse();
            }
            try (var mapperXml = getClass().getClassLoader().getResourceAsStream("mapper/dynamicform/DynamicFormInstanceMapper.xml")) {
                assertNotNull(mapperXml);
                new XMLMapperBuilder(mapperXml, configuration, "entity-manual-boundary", configuration.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
                var manual = session.getMapper(PlatformDynamicFormInstanceMapper.class);
                assertEquals(1L, manual.selectCountPage(new DynamicFormInstancePageQuery(1L, null, null, 0, 20)));
                assertEquals(0, manual.updateValueIfMatch(new DynamicFormInstanceValueUpdate(1L, 201L, 1, "{}", "fixture")));
                assertEquals(0, manual.updateValueIfMatch(new DynamicFormInstanceValueUpdate(1L, 206L, 1, "{}", "fixture")));
                assertEquals(0, manual.updateValueIfMatch(new DynamicFormInstanceValueUpdate(1L, 207L, 1, "{}", "fixture")));
                assertEquals(1, manual.updateValueIfMatch(new DynamicFormInstanceValueUpdate(1L, 205L, 1, "{\"name\":\"manual\"}", "fixture")));
                var mapper = session.getMapper(RequirementAnalysisRootMapper.class);
                assertEquals(1, mapper.updateEntityDataIfMatch(new RequirementAnalysisEntityDataUpdate(
                        1L, 101L, 3, "{\"name\":\"entity\",\"enabled\":false,\"count\":0}", "fixture")));
                assertEquals("entity", value(connection, "SELECT entity_value_json->>'$.name' FROM sol_preparation WHERE id=101"));
                assertEquals(source, value(connection, "SELECT value_json FROM plt_dynamic_form_instance WHERE id=201"));
                assertEquals(0, mapper.updateEntityDataIfMatch(new RequirementAnalysisEntityDataUpdate(1L, 101L, 3, "{}", "fixture")));
                assertEquals(0, mapper.updateEntityDataIfMatch(new RequirementAnalysisEntityDataUpdate(2L, 101L, 4, "{}", "fixture")));
                assertEquals(0, mapper.updateEntityDataIfMatch(new RequirementAnalysisEntityDataUpdate(1L, 102L, 4, "{}", "fixture")));
                session.rollback();
                assertEquals(source, value(connection, "SELECT entity_value_json FROM sol_preparation WHERE id=101"));
                assertEquals("3", value(connection, "SELECT version FROM sol_preparation WHERE id=101"));
            }
        }
    }

    private String value(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
