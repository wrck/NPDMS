package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectExecutionNodeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.LocalDateTime;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.*;

class ProjectExecutionNodePersistenceTest {
    private EmbeddedDatabase database;
    private SqlSession session;
    private JdbcTemplate jdbc;
    private ProjectStageInstanceMapper stages;
    private ProjectTaskInstanceMapper tasks;
    private ProjectRuntimeGraphMapper graph;
    private final LocalDateTime start = LocalDateTime.of(2026, 9, 15, 9, 0, 0, 123_000_000);

    @BeforeEach void setup() throws Exception {
        // Dedicated H2 connection only; never load application configuration or external credentials.
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("SET MODE MySQL");
        String common = "id BIGINT PRIMARY KEY,project_id BIGINT,name VARCHAR(100),sort_order INT,source_definition_id BIGINT,"
                + "plan_start_time TIMESTAMP(3),plan_end_time TIMESTAMP(3),actual_start_time TIMESTAMP(3),actual_end_time TIMESTAMP(3),"
                + "status VARCHAR(30),version INT,tenant_id BIGINT,deleted BOOLEAN DEFAULT FALSE,"
                + "creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP(3),update_time TIMESTAMP(3)";
        jdbc.execute("CREATE TABLE proj_project_stage (" + common + ",stage_code VARCHAR(64),entry_criteria VARCHAR(500),exit_criteria VARCHAR(500),"
                + "suggested_start_time TIMESTAMP(3),suggested_end_time TIMESTAMP(3),deviation_reason VARCHAR(500),"
                + "responsible_role VARCHAR(64),responsible_user_id BIGINT,definition_revision_id BIGINT,graph_version BIGINT,start_node BOOLEAN,terminal_node BOOLEAN)");
        jdbc.execute("CREATE TABLE proj_project_task (" + common + ",task_code VARCHAR(64),stage_code VARCHAR(64),parent_task_code VARCHAR(64),"
                + "parent_task_id BIGINT,root_task_id BIGINT,tree_depth INT,business_level_code VARCHAR(64),milestone_id BIGINT,progress DECIMAL(8,2),"
                + "state_machine_revision_id BIGINT,priority INT,estimated_hours DECIMAL(10,2),satisfaction_timing VARCHAR(64),"
                + "acc_satisfaction_template_id BIGINT,template_revision_id BIGINT,template_version INT,satisfaction_rule_version VARCHAR(64),"
                + "satisfaction_threshold DECIMAL(8,2),description VARCHAR(500),description_format VARCHAR(64))");
        // Execute the actual additive migration; seeded historical rows must remain empty for new fields.
        jdbc.execute("INSERT INTO proj_project_stage (id,project_id,stage_code) VALUES (91,19,'OLD_STAGE')");
        jdbc.execute("INSERT INTO proj_project_task (id,project_id,task_code) VALUES (92,19,'OLD_TASK')");
        try (var connection = database.getConnection()) {
            var migration = new FileSystemResource("../sql/migrations/V247__project_execution_node_times.sql");
            // H2 does not support MySQL's multi-ADD syntax; preserve every real column definition.
            var sql = migration.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll(",\\s+ADD COLUMN", "; ALTER TABLE proj_project_task ADD COLUMN");
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)), java.nio.charset.StandardCharsets.UTF_8));
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("execution-node", new JdbcTransactionFactory(), database));
        configuration.addMapper(ProjectStageInstanceMapper.class);
        configuration.addMapper(ProjectTaskInstanceMapper.class);
        String resource = "mapper/runtimegraph/ProjectRuntimeGraphMapper.xml";
        try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        stages = session.getMapper(ProjectStageInstanceMapper.class);
        tasks = session.getMapper(ProjectTaskInstanceMapper.class);
        graph = session.getMapper(ProjectRuntimeGraphMapper.class);
    }

    @AfterEach void close() {
        if (session != null) session.close();
        if (database != null) database.shutdown();
    }

    @Test void bothTablesPersistInheritedTimesAndKeepOwnCodeSeparateFromTaskStage() {
        var stage = common(new ProjectStageInstanceDO()).setId(11L).setCode("PREP").setEntryCriteria("ready");
        var task = common(new ProjectTaskInstanceDO()).setId(12L).setCode("SURVEY").setStageCode("PREP").setTreeDepth(0);
        assertEquals(1, stages.insert(stage));
        assertEquals(1, tasks.insert(task));
        var stageRead = stages.selectByProjectIdAndStageCode(9L,"PREP");
        var taskRead = tasks.selectByProjectIdAndTaskCode(9L,"SURVEY");
        assertTimes(stageRead); assertTimes(taskRead);
        assertEquals("PREP",stageRead.getCode());
        assertEquals("SURVEY",taskRead.getCode()); assertEquals("PREP",taskRead.getStageCode());
        var query = new ProjectRuntimeGraphQuery(1L,9L);
        assertTimes(graph.selectStages(query).getFirst());
        assertTimes(graph.selectTasks(query).getFirst());
        assertEquals("PREP",graph.selectStages(query).getFirst().getCode());
        assertEquals("SURVEY",graph.selectTasks(query).getFirst().getCode());
        assertEquals("PREP",graph.selectTasks(query).getFirst().getStageCode());
        assertTrue(graph.selectTasks(new ProjectRuntimeGraphQuery(2L,9L)).isEmpty());
        assertTrue(graph.selectStages(new ProjectRuntimeGraphQuery(1L,10L)).isEmpty());
    }

    @Test void updatesKeepAcceptanceIndependentAndMigrationDoesNotBackfillHistory() {
        assertNull(stages.selectById(91L).getAcceptanceTime());
        var oldTask = tasks.selectById(92L);
        assertNull(oldTask.getSuggestedStartTime()); assertNull(oldTask.getSuggestedEndTime()); assertNull(oldTask.getAcceptanceTime());
        var stage = common(new ProjectStageInstanceDO()).setId(11L).setCode("PREP");
        var task = common(new ProjectTaskInstanceDO()).setId(12L).setCode("SURVEY").setStageCode("PREP");
        stages.insert(stage); tasks.insert(task);
        stages.updateById(new ProjectStageInstanceDO().setId(11L).setActualEndTime(start.plusDays(5)).setVersion(2));
        tasks.updateById(new ProjectTaskInstanceDO().setId(12L).setActualEndTime(start.plusDays(5)).setVersion(2));
        assertEquals(start.plusDays(3),stages.selectById(11L).getAcceptanceTime());
        assertEquals(start.plusDays(3),tasks.selectById(12L).getAcceptanceTime());
        assertEquals(start.plusDays(5),tasks.selectById(12L).getActualEndTime());
        assertEquals(2,stages.selectById(11L).getVersion());
    }

    private <T extends ProjectExecutionNodeDO<T>> T common(T node) {
        node.setTenantId(1L);
        node.setProjectId(9L).setName("实例").setStatus("ACTIVE").setVersion(1).setSortOrder(2)
                .setSuggestedStartTime(start).setSuggestedEndTime(start.plusDays(1))
                .setPlanStartTime(start.plusHours(1)).setPlanEndTime(start.plusDays(2))
                .setActualStartTime(start.plusHours(2)).setActualEndTime(start.plusDays(2))
                .setAcceptanceTime(start.plusDays(3));
        return node;
    }

    private void assertTimes(ProjectExecutionNodeDO<?> node) {
        assertEquals(start,node.getSuggestedStartTime()); assertEquals(start.plusDays(1),node.getSuggestedEndTime());
        assertEquals(start.plusHours(1),node.getPlanStartTime()); assertEquals(start.plusDays(2),node.getPlanEndTime());
        assertEquals(start.plusHours(2),node.getActualStartTime()); assertEquals(start.plusDays(2),node.getActualEndTime());
        assertEquals(start.plusDays(3),node.getAcceptanceTime());
        assertEquals(9L,node.getProjectId()); assertEquals(1,node.getVersion());
    }
}
