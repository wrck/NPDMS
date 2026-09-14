package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit fixed-test fixture only. Every write is rolled back; never publishes a real project plan. */
@EnabledIfSystemProperty(named="pms.plan.activation.fixture",matches="993109130054")
class ProjectPlanActivationMySqlTest {
    @Test void productionSqlMovesTheVersionAtomicallyAndRollbackPreservesTheBrowserFixture() throws Exception {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME"));
        assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        String user = System.getenv("NPDMS_DB_USER"), password = System.getenv("NPDMS_DB_PASSWORD");
        assertNotNull(user); assertNotNull(password);
        var database = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",user,password);
        var jdbc = new JdbcTemplate(database);
        long projectId = Long.getLong("pms.plan.activation.fixture");
        assertEquals("专项改版验收",jdbc.queryForObject("SELECT project_name FROM proj_project WHERE tenant_id=1 AND id=? AND deleted=0",String.class,projectId));
        var oldProject = jdbc.queryForMap("SELECT * FROM proj_project WHERE tenant_id=1 AND id=?",projectId);
        var oldPlans = jdbc.queryForList("SELECT * FROM proj_project_plan_version WHERE tenant_id=1 AND project_id=? ORDER BY id",projectId);
        var oldRounds = jdbc.queryForList("SELECT * FROM proj_project_node_execution WHERE tenant_id=1 AND project_id=? ORDER BY id",projectId);
        var configuration = new Configuration(new Environment("fixed-test-plan",new SpringManagedTransactionFactory(),database));
        for (String mapper : List.of("ProjectPlanVersionMapper","ProjectNodeExecutionMapper")) {
            String resource = "mapper/projectplan/"+mapper+".xml";
            try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        var persistence = new ProjectPlanActivationPersistence(session.getMapper(ProjectPlanVersionMapper.class),session.getMapper(ProjectNodeExecutionMapper.class));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
        TenantContextHolder.setTenantId(1L);
        try {
            transaction.executeWithoutResult(status -> {
                // Rollback is unconditional, including successful assertions. The existing draft is not consumed.
                status.setRollbackOnly();
                var project = jdbc.queryForMap("SELECT * FROM proj_project WHERE tenant_id=1 AND id=? FOR UPDATE",projectId);
                assertEquals("ACTIVE",project.get("lifecycle_status"));
                long oldId = ((Number)project.get("active_plan_version_id")).longValue();
                var draft = jdbc.queryForMap("SELECT * FROM proj_project_plan_version WHERE tenant_id=1 AND project_id=? AND status='DRAFT' AND deleted=0 FOR UPDATE",projectId);
                long newId = ((Number)draft.get("id")).longValue();
                var rounds = jdbc.queryForList("SELECT * FROM proj_project_node_execution WHERE tenant_id=1 AND project_id=? AND current_marker=1 AND status IN ('PENDING','ACTIVE') AND deleted=0 FOR UPDATE",projectId);
                assertFalse(rounds.isEmpty());
                var changes = rounds.stream().map(row -> new ProjectNodeExecutionMapper.PlanRebase(1L,projectId,
                        ((Number)row.get("id")).longValue(),((Number)row.get("version")).intValue(),oldId,newId,
                        ((Number)row.get("contract_id")).longValue(),((Number)row.get("contract_id")).longValue(),"plan_sql_acceptance")).toList();
                String snapshot = jdbc.queryForObject("SELECT execution_snapshot FROM proj_project_plan_version WHERE tenant_id=1 AND project_id=? AND id=?",String.class,projectId,oldId);
                persistence.activate(new ProjectPlanVersionMapper.Activation(1L,projectId,oldId,newId,
                        ((Number)draft.get("version")).intValue(),((Number)project.get("version")).intValue(),snapshot,
                        LocalDateTime.now(),"plan_sql_acceptance"),changes,List.of());
                assertEquals(newId,jdbc.queryForObject("SELECT active_plan_version_id FROM proj_project WHERE tenant_id=1 AND id=?",Long.class,projectId));
                assertEquals(rounds.size(),jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_node_execution WHERE tenant_id=1 AND project_id=? AND plan_version_id=? AND current_marker=1",Integer.class,projectId,newId));
                assertEquals("SUPERSEDED",jdbc.queryForObject("SELECT status FROM proj_project_plan_version WHERE tenant_id=1 AND id=?",String.class,oldId));
            });
        } finally { TenantContextHolder.clear(); }
        assertTrue(oldProject.equals(jdbc.queryForMap("SELECT * FROM proj_project WHERE tenant_id=1 AND id=?",projectId)),"Project changed after rollback");
        assertTrue(oldPlans.equals(jdbc.queryForList("SELECT * FROM proj_project_plan_version WHERE tenant_id=1 AND project_id=? ORDER BY id",projectId)),"Plan or draft changed after rollback");
        assertTrue(oldRounds.equals(jdbc.queryForList("SELECT * FROM proj_project_node_execution WHERE tenant_id=1 AND project_id=? ORDER BY id",projectId)),"Execution history changed after rollback");
    }
}
