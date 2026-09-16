package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectStageManagementPersistenceTest {
    EmbeddedDatabase database;
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    ProjectStageInstanceMapper stages;
    ProjectPlanProjectionMapper projections;
    final LocalDateTime start = LocalDateTime.of(2026, 9, 15, 9, 0, 0, 123_000_000);

    @BeforeEach void setup() throws Exception {
        // A generated, isolated H2 database; no application configuration or external datasource.
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("SET MODE MySQL");
        jdbc.execute("CREATE TABLE proj_project_stage (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,"
                + "stage_code VARCHAR(32),name VARCHAR(128),sort_order INT,entry_criteria VARCHAR(500),exit_criteria VARCHAR(500),"
                + "start_node BOOLEAN,terminal_node BOOLEAN,status VARCHAR(32),version INT,deleted INT,updater VARCHAR(64),update_time TIMESTAMP(3),"
                + "suggested_start_time TIMESTAMP(3),suggested_end_time TIMESTAMP(3),plan_start_time TIMESTAMP(3),plan_end_time TIMESTAMP(3),"
                + "actual_start_time TIMESTAMP(3),actual_end_time TIMESTAMP(3),acceptance_time TIMESTAMP(3),deviation_reason VARCHAR(500),responsible_role VARCHAR(64),responsible_user_id BIGINT)");
        jdbc.execute("INSERT INTO proj_project_stage (id,tenant_id,project_id,stage_code,name,status,version,deleted,responsible_role,responsible_user_id) "
                + "VALUES (11,1,9,'PREP','工前准备','PENDING',0,0,'RESERVED',73)");
        var configuration = new Configuration(new Environment("stage-management", new SpringManagedTransactionFactory(), database));
        for (String resource : List.of("mapper/projectmanual/ProjectStageInstanceMapper.xml", "mapper/projectplan/ProjectPlanProjectionMapper.xml")) {
            try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml);
                // H2 lacks MySQL bit literals; only normalize that literal, retaining the real statement and predicates.
                var sql = new String(xml.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("b'0'", "0");
                new XMLMapperBuilder(new java.io.StringReader(sql), configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        stages = session.getMapper(ProjectStageInstanceMapper.class);
        projections = session.getMapper(ProjectPlanProjectionMapper.class);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
    }

    @AfterEach void close() { database.shutdown(); }

    @Test void transitionTimeIsWrittenImmediatelyAndReworkClearsOnlyCurrentSummary() {
        assertEquals(1, transition(0, "PENDING", "ACTIVE", start));
        assertEquals(start, time("actual_start_time")); assertNull(time("actual_end_time"));
        assertEquals(1, transition(1, "ACTIVE", "DONE", start.plusMinutes(20)));
        assertEquals(start, time("actual_start_time")); assertEquals(start.plusMinutes(20), time("actual_end_time"));
        var completed = jdbc.queryForMap("SELECT * FROM proj_project_stage WHERE id=11");
        assertEquals(0, transition(1, "ACTIVE", "DONE", start.plusHours(1)));
        assertEquals(completed, jdbc.queryForMap("SELECT * FROM proj_project_stage WHERE id=11"));
        assertEquals(1, transition(2, "DONE", "PENDING", start.plusDays(1)));
        assertNull(time("actual_start_time")); assertNull(time("actual_end_time"));
        assertEquals(1, transition(3, "PENDING", "ACTIVE", start.plusDays(2)));
        assertEquals(start.plusDays(2), time("actual_start_time"));
        assertEquals(1, transition(4, "ACTIVE", "TERMINATED", start.plusDays(3)));
        assertEquals(start.plusDays(3), time("actual_end_time"));
    }

    @Test void templatePlanUpdatesCannotOverwriteOrClearInstanceManagementFields() {
        assertEquals(1, transition(0, "PENDING", "ACTIVE", start));
        jdbc.update("UPDATE proj_project_stage SET suggested_start_time=?,suggested_end_time=?,plan_start_time=?,plan_end_time=?,acceptance_time=?,deviation_reason=? WHERE id=11",
                start,start.plusDays(1),start.plusHours(1),start.plusDays(2),start.plusDays(3),"现场协调");
        var definition = new ProjectStageInstanceDO();
        definition.setCode("PREP"); definition.setName("工前准备");
        assertEquals(1, projections.updateStageDefinition(new ProjectPlanProjectionMapper.StageDefinitionUpdate(1L,9L,11L,1,definition,"editor")));
        assertEquals(start.plusHours(1), time("plan_start_time"));
        assertEquals(start.plusDays(2), time("plan_end_time"));
        definition.setSuggestedStartTime(start.plusDays(99)); definition.setSuggestedEndTime(start.plusDays(99));
        definition.setAcceptanceTime(start.plusDays(99));
        definition.setPlanStartTime(start.plusDays(99)); definition.setPlanEndTime(start.plusDays(99)); definition.setDeviationReason("IGNORED");
        definition.setActualStartTime(start.plusDays(99)); definition.setResponsibleRole("IGNORED"); definition.setResponsibleUserId(99L);
        assertEquals(1, projections.updateStageDefinition(new ProjectPlanProjectionMapper.StageDefinitionUpdate(1L,9L,11L,2,definition,"editor")));
        assertEquals(start, time("suggested_start_time")); assertEquals(start.plusDays(1), time("suggested_end_time"));
        assertEquals(start.plusDays(3), time("acceptance_time"));
        assertEquals(start.plusHours(1), time("plan_start_time")); assertEquals(start.plusDays(2), time("plan_end_time"));
        assertEquals("现场协调",jdbc.queryForObject("SELECT deviation_reason FROM proj_project_stage WHERE id=11", String.class));
        assertEquals(start, time("actual_start_time")); assertNull(time("actual_end_time"));
        assertEquals("RESERVED", jdbc.queryForObject("SELECT responsible_role FROM proj_project_stage WHERE id=11", String.class));
        assertEquals(73L, jdbc.queryForObject("SELECT responsible_user_id FROM proj_project_stage WHERE id=11", Long.class));
    }

    @Test void tenantProjectStatusAndVersionMismatchesCannotWriteDates() {
        for (var query : List.of(
                new ProjectStageStatusUpdate(2L,9L,11L,0,"PENDING","ACTIVE","1",start),
                new ProjectStageStatusUpdate(1L,8L,11L,0,"PENDING","ACTIVE","1",start),
                new ProjectStageStatusUpdate(1L,9L,11L,9,"PENDING","ACTIVE","1",start),
                new ProjectStageStatusUpdate(1L,9L,11L,0,"DONE","ACTIVE","1",start))) {
            assertEquals(0, stages.updateStatusIfMatch(query));
        }
        assertNull(time("actual_start_time"));
    }

    @Test void laterTransactionFailureRollsBackStatusAndActualTimeTogether() {
        var before = jdbc.queryForMap("SELECT * FROM proj_project_stage WHERE id=11");
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            assertEquals(1, transition(0, "PENDING", "ACTIVE", start));
            throw new IllegalStateException("execution record or outbox write failed");
        }));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM proj_project_stage WHERE id=11"));
    }

    int transition(int version, String expected, String target, LocalDateTime at) {
        return stages.updateStatusIfMatch(new ProjectStageStatusUpdate(1L,9L,11L,version,expected,target,"1",at));
    }
    LocalDateTime time(String column) {
        // Test-only constants, never request data.
        return jdbc.queryForObject("SELECT " + column + " FROM proj_project_stage WHERE id=11", LocalDateTime.class);
    }
}
