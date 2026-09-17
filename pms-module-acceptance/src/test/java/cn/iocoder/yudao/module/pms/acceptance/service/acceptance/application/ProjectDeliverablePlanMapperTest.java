package cn.iocoder.yudao.module.pms.acceptance.service.acceptance.application;

import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
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
import static org.junit.jupiter.api.Assertions.*;

class ProjectDeliverablePlanMapperTest {
    @Test void definitionChangesPreserveBusinessEvidenceAndRollbackWithThePlan() throws Exception {
        // Isolated H2 only, production SQL; no application profiles, credentials or external database.
        var database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc=new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE acc_project_deliverable (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,deliverable_code VARCHAR(64),name VARCHAR(128),stage_code VARCHAR(32),task_code VARCHAR(64),required BOOLEAN,source_definition_id BIGINT,status VARCHAR(32),current_source_version_id BIGINT,archive_status VARCHAR(32),version INT,deleted INT,updater VARCHAR(64),update_time TIMESTAMP,active_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN deliverable_code ELSE NULL END),UNIQUE(tenant_id,project_id,active_code))");
            jdbc.execute("CREATE TABLE acc_project_deliverable_source_version (id BIGINT,tenant_id BIGINT,deliverable_id BIGINT,relation_status VARCHAR(32),deleted INT)");
            jdbc.execute("CREATE TABLE acc_acceptance (id BIGINT,tenant_id BIGINT,deliverable_id BIGINT)");
            jdbc.execute("CREATE TABLE acc_satisfaction_collection_task (id BIGINT,tenant_id BIGINT,deliverable_id BIGINT)");
            jdbc.execute("INSERT INTO acc_project_deliverable (id,tenant_id,project_id,deliverable_code,name,stage_code,task_code,required,source_definition_id,status,current_source_version_id,archive_status,version,deleted) VALUES (10,1,9,'D10','old A','PREP','T1',TRUE,101,'SUBMITTED',88,'VALID',3,0),(20,1,9,'D20','old B','PREP',NULL,FALSE,102,'PENDING',NULL,NULL,3,0),(30,1,9,'D30','handled then withdrawn','PREP',NULL,FALSE,103,'PENDING',NULL,'INVALID',3,0),(40,2,9,'D10','other tenant','PREP',NULL,FALSE,104,'PENDING',NULL,NULL,3,0)");
            jdbc.execute("INSERT INTO acc_project_deliverable_source_version VALUES (88,1,10,'CURRENT',0),(89,1,30,'SUPERSEDED',0)");
            var original=jdbc.queryForList("SELECT * FROM acc_project_deliverable ORDER BY id");
            var sources=jdbc.queryForList("SELECT * FROM acc_project_deliverable_source_version ORDER BY id");
            var configuration=new Configuration(new Environment("deliverable-plan",new SpringManagedTransactionFactory(),database));
            configuration.setMapUnderscoreToCamelCase(true);
            String resource="mapper/acceptance/AccProjectDeliverableMapper.xml";
            try (var xml=getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
            var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
            var mapper=session.getMapper(AccProjectDeliverableMapper.class);
            var transaction=new TransactionTemplate(new DataSourceTransactionManager(database));
            var planScope=new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.ProjectDeliverablePlanScopeQuery(1L,9L);
            transaction.executeWithoutResult(status -> {
                assertEquals(java.util.List.of(10L,20L,30L),mapper.selectPlanDefinitionsForUpdate(planScope).stream().map(AccProjectDeliverableDO::getId).toList());
                assertEquals(java.util.List.of(20L),mapper.selectRetirablePlanDefinitionIds(planScope));
            });
            Runnable swap=()->{
                assertEquals(1,mapper.stagePlanCodeForRename(change(1L,9L,10L,"D20")));
                assertEquals(1,mapper.stagePlanCodeForRename(change(1L,9L,20L,"D10")));
                assertEquals(1,mapper.updatePlanDefinition(change(1L,9L,10L,"D20")));
                assertEquals(1,mapper.updatePlanDefinition(change(1L,9L,20L,"D10")));
            };
            assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status -> { swap.run(); throw new IllegalStateException("later plan or Outbox write failed"); }));
            assertEquals(original,jdbc.queryForList("SELECT * FROM acc_project_deliverable ORDER BY id"));
            transaction.executeWithoutResult(status -> swap.run());
            var handled=jdbc.queryForMap("SELECT * FROM acc_project_deliverable WHERE id=10");
            assertEquals("D20",handled.get("DELIVERABLE_CODE")); assertEquals("new definition",handled.get("NAME"));
            assertEquals("CUSTOM",handled.get("STAGE_CODE")); assertNull(handled.get("TASK_CODE"));
            assertEquals(4,handled.get("VERSION")); assertEquals("SUBMITTED",handled.get("STATUS"));
            assertEquals(88L,handled.get("CURRENT_SOURCE_VERSION_ID")); assertEquals("VALID",handled.get("ARCHIVE_STATUS"));
            assertEquals(101L,handled.get("SOURCE_DEFINITION_ID"));
            assertEquals(original.get(3),jdbc.queryForMap("SELECT * FROM acc_project_deliverable WHERE id=40"));
            assertEquals(sources,jdbc.queryForList("SELECT * FROM acc_project_deliverable_source_version ORDER BY id"));
            // A withdrawn source is still handling history, even though the current pointer is empty.
            assertEquals(0,mapper.retireUnhandledForPlan(change(1L,9L,30L,null)));
            assertEquals(0,mapper.retireUnhandledForPlan(new AccProjectDeliverableMapper.PlanDefinitionChange(1L,9L,10L,4,null,"7")));
            assertEquals(0,mapper.updatePlanDefinition(change(2L,9L,10L,"FOREIGN")));
            assertEquals(0,mapper.updatePlanDefinition(change(1L,99L,30L,"FOREIGN")));
            assertEquals(0,mapper.updatePlanDefinition(change(1L,9L,10L,"STALE")));
            var pending=new AccProjectDeliverableMapper.PlanDefinitionChange(1L,9L,20L,4,null,"7");
            transaction.executeWithoutResult(status -> {
                jdbc.execute("INSERT INTO acc_acceptance VALUES (60,1,20)");
                assertTrue(mapper.selectRetirablePlanDefinitionIds(planScope).isEmpty());
                assertEquals(0,mapper.retireUnhandledForPlan(pending));
                status.setRollbackOnly();
            });
            transaction.executeWithoutResult(status -> {
                jdbc.execute("INSERT INTO acc_satisfaction_collection_task VALUES (61,1,20)");
                assertTrue(mapper.selectRetirablePlanDefinitionIds(planScope).isEmpty());
                assertEquals(0,mapper.retireUnhandledForPlan(pending));
                status.setRollbackOnly();
            });
            assertEquals(1,mapper.retireUnhandledForPlan(pending));
            assertEquals("D10",jdbc.queryForObject("SELECT deliverable_code FROM acc_project_deliverable WHERE id=20",String.class));
            // Retiring frees the active code, not the archived original identity or data.
            jdbc.execute("INSERT INTO acc_project_deliverable (id,tenant_id,project_id,deliverable_code,status,version,deleted) VALUES (50,1,9,'D10','PENDING',0,0)");
            assertEquals(1,jdbc.queryForObject("SELECT deleted FROM acc_project_deliverable WHERE id=20",Integer.class));
        } finally { database.shutdown(); }
    }
    private AccProjectDeliverableMapper.PlanDefinitionChange change(Long tenant,Long project,Long id,String code) {
        var definition=new AccProjectDeliverableDO(); definition.setDeliverableCode(code); definition.setName("new definition");
        definition.setStageCode("CUSTOM"); definition.setRequired(false);
        return new AccProjectDeliverableMapper.PlanDefinitionChange(tenant,project,id,3,definition,"7");
    }
}
