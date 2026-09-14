package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness.ProjectTaskBusinessLinkDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.*;
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
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class StageBusinessAssociationsMapperTest {
    @Test void taskAndStageWithTheSameNumericIdRemainDistinctAndStageHistorySurvivesRollback() throws Exception {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE proj_task_business_link (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,task_id BIGINT,stage_id BIGINT,"
                    + "node_execution_id BIGINT,execution_contract_id BIGINT,contract_version INT,owner_context VARCHAR(64),object_type VARCHAR(128),"
                    + "object_id VARCHAR(128),fact_version VARCHAR(256),linked_by BIGINT,linked_at TIMESTAMP,unlinked_by BIGINT,unlinked_at TIMESTAMP,"
                    + "active_marker INT GENERATED ALWAYS AS (CASE WHEN unlinked_at IS NULL THEN 1 ELSE NULL END),version INT,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,"
                    + "CHECK ((task_id IS NOT NULL AND stage_id IS NULL) OR (task_id IS NULL AND stage_id IS NOT NULL)),"
                    + "UNIQUE(tenant_id,task_id,owner_context,object_type,object_id,active_marker),UNIQUE(tenant_id,stage_id,owner_context,object_type,object_id,active_marker))");
            var configuration = new Configuration(new Environment("stage-associations",new SpringManagedTransactionFactory(),database));
            configuration.setMapUnderscoreToCamelCase(true);
            try (var xml = getClass().getClassLoader().getResourceAsStream("mapper/taskbusiness/ProjectTaskBusinessLinkMapper.xml")) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,"stage-associations",configuration.getSqlFragments()).parse();
            }
            var mapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(ProjectTaskBusinessLinkMapper.class);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
            var task = link(51L,70L,"41"); task.setTaskId(90L); task.setStageId(null);
            var stage = link(52L,71L,"41");
            transaction.executeWithoutResult(status -> { assertEquals(1,mapper.insertLink(task)); assertEquals(1,mapper.insertLink(stage)); });
            var taskQuery = new TaskBusinessLinksQuery(1L,9L,90L);
            var stageQuery = TaskBusinessLinksQuery.stage(1L,9L,90L);
            assertEquals(51L,mapper.selectActive(taskQuery).getFirst().getId());
            assertEquals(52L,mapper.selectActive(stageQuery).getFirst().getId());
            assertTrue(mapper.selectActive(new TaskBusinessLinksQuery(1L,9L,null,null)).isEmpty());
            assertTrue(mapper.selectActive(new TaskBusinessLinksQuery(1L,9L,90L,90L)).isEmpty());
            assertTrue(mapper.selectActive(TaskBusinessLinksQuery.stage(2L,9L,90L)).isEmpty());
            assertTrue(mapper.selectActive(TaskBusinessLinksQuery.stage(1L,10L,90L)).isEmpty());
            var originalTask = jdbc.queryForMap("SELECT * FROM proj_task_business_link WHERE id=51");
            var originalStage = jdbc.queryForMap("SELECT * FROM proj_task_business_link WHERE id=52");
            var close = new TaskBusinessUnlinkUpdate(1L,9L,null,52L,0,0L,LocalDateTime.of(2026,9,14,12,0),90L);
            assertThrows(IllegalStateException.class,() -> transaction.executeWithoutResult(status -> {
                assertEquals(1,mapper.unlinkIfMatch(close));
                assertEquals(1,mapper.insertLink(link(53L,72L,"42")));
                throw new IllegalStateException("later association write failed");
            }));
            assertEquals(originalStage,jdbc.queryForMap("SELECT * FROM proj_task_business_link WHERE id=52"));
            assertEquals(1,mapper.selectActive(stageQuery).size());
            transaction.executeWithoutResult(status -> {
                assertEquals(1,mapper.unlinkIfMatch(close));
                assertEquals(0,mapper.unlinkIfMatch(close));
                assertEquals(1,mapper.insertLink(link(53L,72L,"42")));
            });
            assertEquals(53L,mapper.selectActive(stageQuery).getFirst().getId());
            assertEquals(Set.of("41"),Set.copyOf(mapper.selectPreviouslyAssociatedObjectIds(new PreviousBusinessAssociationsQuery(
                    1L,9L,null,72L,"SOL","REQUIREMENT_ANALYSIS",Set.of("41","42"),90L))));
            assertTrue(mapper.selectPreviouslyAssociatedObjectIds(new PreviousBusinessAssociationsQuery(1L,9L,null,72L,"SOL","REQUIREMENT_ANALYSIS",Set.of(),90L)).isEmpty());
            assertEquals(71L,jdbc.queryForObject("SELECT node_execution_id FROM proj_task_business_link WHERE id=52",Long.class));
            assertEquals(originalTask,jdbc.queryForMap("SELECT * FROM proj_task_business_link WHERE id=51"));
        } finally { database.shutdown(); }
    }

    private ProjectTaskBusinessLinkDO link(Long id,Long executionId,String objectId) {
        var row = new ProjectTaskBusinessLinkDO(); row.setId(id); row.setTenantId(1L); row.setProjectId(9L); row.setStageId(90L);
        row.setNodeExecutionId(executionId); row.setExecutionContractId(31L); row.setContractVersion(1);
        row.setOwnerContext("SOL"); row.setObjectType("REQUIREMENT_ANALYSIS"); row.setObjectId(objectId); row.setFactVersion("v1");
        row.setLinkedBy(0L); row.setLinkedAt(LocalDateTime.of(2026,9,14,10,0)); row.setVersion(0); row.setCreator("0"); row.setUpdater("0");
        row.setCreateTime(row.getLinkedAt()); row.setUpdateTime(row.getLinkedAt()); return row;
    }
}
