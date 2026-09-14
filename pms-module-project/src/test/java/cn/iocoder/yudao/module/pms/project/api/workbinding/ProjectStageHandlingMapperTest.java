package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStageHandlingMapperTest {
    @Test void startUsesOwnerTransactionAndNeverSubmitsCompletesOrRewritesHistory() throws Exception {
        // Only this in-memory database is used; no application/Docker configuration is loaded.
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE proj_project_node_execution (id BIGINT PRIMARY KEY, tenant_id BIGINT, project_id BIGINT,"
                    + "plan_version_id BIGINT, contract_id BIGINT, node_kind VARCHAR(10), current_marker INT, status VARCHAR(30),"
                    + "started_at TIMESTAMP, started_plan_version_id BIGINT, submitted_at TIMESTAMP, ended_at TIMESTAMP,"
                    + "result_snapshot VARCHAR(100), version INT, deleted INT, updater VARCHAR(64), update_time TIMESTAMP)");
            jdbc.execute("INSERT INTO proj_project_node_execution VALUES (101,1,9,100,99,'STAGE',1,'ACTIVE',NULL,NULL,NULL,NULL,NULL,1,0,'original',CURRENT_TIMESTAMP),"
                    + "(102,1,9,80,79,'STAGE',NULL,'DONE',CURRENT_TIMESTAMP,80,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'old result',7,0,'original',CURRENT_TIMESTAMP)");
            jdbc.execute("INSERT INTO proj_project_node_execution VALUES (103,1,9,100,98,'STAGE',1,'PENDING',NULL,NULL,NULL,NULL,NULL,1,0,'original',CURRENT_TIMESTAMP)");
            var configuration = new Configuration(new Environment("stage-handling",new SpringManagedTransactionFactory(),database));
            try (var xml = getClass().getClassLoader().getResourceAsStream("mapper/projectplan/ProjectNodeExecutionMapper.xml")) {
                assertNotNull(xml);
                new XMLMapperBuilder(xml,configuration,"stage-handling",configuration.getSqlFragments()).parse();
            }
            var mapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(ProjectNodeExecutionMapper.class);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
            var original = jdbc.queryForMap("SELECT * FROM proj_project_node_execution WHERE id=101");
            var history = jdbc.queryForMap("SELECT * FROM proj_project_node_execution WHERE id=102");
            var now = LocalDateTime.of(2026,9,14,10,0);
            var start = new ProjectNodeExecutionMapper.StageHandlingStart(1L,9L,101L,100L,99L,1,now,9L);
            assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
                assertEquals(1, mapper.beginStageHandlingIfCurrent(start));
                throw new IllegalStateException("Owner form creation failed");
            }));
            assertEquals(original, jdbc.queryForMap("SELECT * FROM proj_project_node_execution WHERE id=101"));
            transaction.executeWithoutResult(status -> {
                assertEquals(0, mapper.beginStageHandlingIfCurrent(new ProjectNodeExecutionMapper.StageHandlingStart(2L,9L,101L,100L,99L,1,now,9L)));
                assertEquals(0, mapper.beginStageHandlingIfCurrent(new ProjectNodeExecutionMapper.StageHandlingStart(1L,10L,101L,100L,99L,1,now,9L)));
                assertEquals(0, mapper.beginStageHandlingIfCurrent(new ProjectNodeExecutionMapper.StageHandlingStart(1L,9L,101L,80L,99L,1,now,9L)));
                assertEquals(0, mapper.beginStageHandlingIfCurrent(new ProjectNodeExecutionMapper.StageHandlingStart(1L,9L,101L,100L,79L,1,now,9L)));
                assertEquals(0, mapper.beginStageHandlingIfCurrent(new ProjectNodeExecutionMapper.StageHandlingStart(1L,9L,102L,80L,79L,7,now,9L)));
                assertEquals(0, mapper.beginStageHandlingIfCurrent(new ProjectNodeExecutionMapper.StageHandlingStart(1L,9L,103L,100L,98L,1,now,9L)));
                assertEquals(1, mapper.beginStageHandlingIfCurrent(start));
                assertEquals(0, mapper.beginStageHandlingIfCurrent(start));
                assertEquals(0, mapper.beginStageHandlingIfCurrent(new ProjectNodeExecutionMapper.StageHandlingStart(1L,9L,101L,100L,99L,2,now.plusHours(1),9L)));
            });
            assertEquals(now, jdbc.queryForObject("SELECT started_at FROM proj_project_node_execution WHERE id=101",java.sql.Timestamp.class).toLocalDateTime());
            assertEquals(100L, jdbc.queryForObject("SELECT started_plan_version_id FROM proj_project_node_execution WHERE id=101",Long.class));
            assertEquals(2, jdbc.queryForObject("SELECT version FROM proj_project_node_execution WHERE id=101",Integer.class));
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM proj_project_node_execution WHERE id=101",String.class));
            assertNull(jdbc.queryForObject("SELECT started_at FROM proj_project_node_execution WHERE id=103",java.sql.Timestamp.class));
            assertNull(jdbc.queryForObject("SELECT submitted_at FROM proj_project_node_execution WHERE id=101",java.sql.Timestamp.class));
            assertNull(jdbc.queryForObject("SELECT ended_at FROM proj_project_node_execution WHERE id=101",java.sql.Timestamp.class));
            assertNull(jdbc.queryForObject("SELECT result_snapshot FROM proj_project_node_execution WHERE id=101",String.class));
            assertEquals(history, jdbc.queryForMap("SELECT * FROM proj_project_node_execution WHERE id=102"));
        } finally { database.shutdown(); }
    }
}
