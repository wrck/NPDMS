package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectStageExecutionLookupQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskExecutionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Read-only real-MySQL verification of explicitly selected browser fixtures; no inserts, deletes or configuration fallback. */
@EnabledIfSystemProperty(named = "pms.stage.fixture.projectId", matches = "[1-9][0-9]*")
class ProjectStageExecutionMapperAcceptanceTest {
    @Test void currentActiveAndEndedStageContextsKeepExactTenantPlanContractAndRoundIdentity() throws Exception {
        assertEquals("npdms_test", System.getenv("NPDMS_DB_NAME"));
        assertEquals("23316", System.getenv("NPDMS_MYSQL_PORT"));
        var database = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"), System.getenv("NPDMS_DB_PASSWORD"));
        var configuration = new Configuration(new Environment("stage-context-acceptance", new JdbcTransactionFactory(), database));
        try (var xml = getClass().getClassLoader().getResourceAsStream("mapper/projectplan/ProjectNodeExecutionMapper.xml")) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, configuration, "stage-context", configuration.getSqlFragments()).parse();
        }
        Long tenant = fixture("tenantId"), project = fixture("projectId"), stage = fixture("stageId"), contract = fixture("contractId");
        TenantContextHolder.setTenantId(tenant);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            var mapper = session.getMapper(ProjectNodeExecutionMapper.class);
            var api = new ProjectNodeExecutionApiImpl(mock(ProjectMasterMapper.class), mock(ProjectTaskExecutionMapper.class), mapper);
            var current = api.inspectStage(new ProjectStageExecutionQuery(project, stage, contract));
            assertTrue(current.writable()); assertEquals(fixture("executionId"), current.executionId());
            assertEquals(fixture("planId"), current.planVersionId());
            var locked = mapper.selectCurrentStageContextForUpdate(new ProjectStageExecutionLookupQuery(tenant, project, stage, contract));
            assertEquals(current.executionId(), locked.executionId());
            var ended = api.inspectStage(new ProjectStageExecutionQuery(project, fixture("endedStageId"), fixture("endedContractId")));
            assertFalse(ended.writable()); assertEquals(2, ended.roundNo());
            assertEquals(fixture("endedExecutionId"), ended.executionId());
            assertNull(mapper.selectCurrentStageContext(new ProjectStageExecutionLookupQuery(tenant,project,fixture("endedStageId"),fixture("oldContractId"))));
            assertNull(mapper.selectCurrentStageContext(new ProjectStageExecutionLookupQuery(tenant+1,project,stage,contract)));
            assertNull(mapper.selectCurrentStageContext(new ProjectStageExecutionLookupQuery(tenant,project+1,stage,contract)));
            assertNull(mapper.selectCurrentStageContext(new ProjectStageExecutionLookupQuery(tenant,project,fixture("endedStageId"),contract)));
            session.rollback();
        } finally { TenantContextHolder.clear(); }
    }

    private Long fixture(String key) {
        Long value = Long.getLong("pms.stage.fixture." + key);
        assertNotNull(value, key); return value;
    }
}
