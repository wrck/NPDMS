package cn.iocoder.yudao.module.pms.project.service.acceptance.application;

import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverablePlanScopeQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.DriverManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Production Owner SQL, exclusively connection-local temporary tables in the isolated test database. */
@EnabledIfSystemProperty(named="pms.deliverable.plan.mysql",matches="true")
class ProjectDeliverablePlanInspectionMySqlTest {
    @Test void previewAndRetirementHaveIdenticalHistoryScopeAndRollbackRules() throws Exception {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME")); assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        try (var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"))) {
            var database=new SingleConnectionDataSource(connection,true); var jdbc=new JdbcTemplate(database);
            jdbc.execute("CREATE TEMPORARY TABLE acc_project_deliverable (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,status VARCHAR(32),current_source_version_id BIGINT,version INT,deleted BIT,updater VARCHAR(64),update_time DATETIME)");
            jdbc.execute("CREATE TEMPORARY TABLE acc_project_deliverable_source_version (id BIGINT,tenant_id BIGINT,deliverable_id BIGINT,deleted BIT)");
            jdbc.execute("CREATE TEMPORARY TABLE acc_acceptance (id BIGINT,tenant_id BIGINT,deliverable_id BIGINT)");
            jdbc.execute("CREATE TEMPORARY TABLE acc_satisfaction_collection_task (id BIGINT,tenant_id BIGINT,deliverable_id BIGINT)");
            jdbc.execute("INSERT INTO acc_project_deliverable VALUES (1,7,80,'PENDING',NULL,3,0,NULL,NULL),(2,7,80,'PENDING',NULL,3,0,NULL,NULL),(3,7,80,'PENDING',NULL,3,0,NULL,NULL),(4,7,80,'PENDING',NULL,3,0,NULL,NULL),(5,8,80,'PENDING',NULL,3,0,NULL,NULL),(6,7,90,'PENDING',NULL,3,0,NULL,NULL),(7,7,80,'SUBMITTED',NULL,3,0,NULL,NULL),(8,7,80,'PENDING',NULL,3,1,NULL,NULL)");
            jdbc.execute("INSERT INTO acc_project_deliverable_source_version VALUES (20,7,2,1)");
            jdbc.execute("INSERT INTO acc_acceptance VALUES (30,7,3),(31,8,1)");
            jdbc.execute("INSERT INTO acc_satisfaction_collection_task VALUES (40,7,4)");
            var configuration=new Configuration(new Environment("temporary-plan-inspection",new SpringManagedTransactionFactory(),database));
            configuration.setMapUnderscoreToCamelCase(true);
            String resource="mapper/acceptance/AccProjectDeliverableMapper.xml";
            try (var xml=getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
            var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(AccProjectDeliverableMapper.class);
            var scope=new ProjectDeliverablePlanScopeQuery(7L,80L);
            var transaction=new TransactionTemplate(new DataSourceTransactionManager(database));
            transaction.executeWithoutResult(status -> {
                assertEquals(List.of(1L,2L,3L,4L,7L),mapper.selectPlanDefinitionsForUpdate(scope).stream().map(row -> row.getId()).toList());
                assertEquals(List.of(1L),mapper.selectRetirablePlanDefinitionIds(scope));
                for (long id:List.of(2L,3L,4L,5L,6L,7L,8L))
                    assertEquals(0,mapper.retireUnhandledForPlan(new AccProjectDeliverableMapper.PlanDefinitionChange(7L,80L,id,3,null,"9")));
                assertEquals(1,mapper.retireUnhandledForPlan(new AccProjectDeliverableMapper.PlanDefinitionChange(7L,80L,1L,3,null,"9")));
                assertTrue(mapper.selectRetirablePlanDefinitionIds(scope).isEmpty());
                status.setRollbackOnly();
            });
            assertEquals(List.of(1L),mapper.selectRetirablePlanDefinitionIds(scope));
            assertEquals(3,jdbc.queryForObject("SELECT version FROM acc_project_deliverable WHERE id=1",Integer.class));
        }
    }
}
