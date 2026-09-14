package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
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

/** Single-connection TEMPORARY table only. No writes to permanent project tables. */
@EnabledIfSystemProperty(named="pms.milestone.plan.mysql",matches="true")
class ProjectPlanMilestoneMySqlTest {
    @Test void definitionSwapPreservesAchievedResultsAndRollsBackWithPlanFailure() throws Exception {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME")); assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        try (var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"))) {
            var database=new SingleConnectionDataSource(connection,true); var jdbc=new JdbcTemplate(database);
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_milestone (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,milestone_code VARCHAR(64),name VARCHAR(128),stage_code VARCHAR(32),timing VARCHAR(64),criteria VARCHAR(500),source_definition_id BIGINT,status VARCHAR(32),version INT,deleted BIT,updater VARCHAR(64),update_time DATETIME,active_milestone_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN milestone_code ELSE NULL END) VIRTUAL,UNIQUE(tenant_id,project_id,active_milestone_code))");
            jdbc.execute("INSERT INTO proj_project_milestone(id,tenant_id,project_id,milestone_code,name,stage_code,source_definition_id,status,version,deleted) VALUES (1,7,80,'M1','achieved','PREP',501,'ACHIEVED',3,0),(2,7,80,'M2','pending','PREP',502,'PENDING',3,0),(3,8,80,'M1','foreign','PREP',503,'PENDING',3,0),(4,7,90,'M1','other project','PREP',504,'PENDING',3,0)");
            var configuration=new Configuration(new Environment("temporary-milestones",new SpringManagedTransactionFactory(),database));
            configuration.setMapUnderscoreToCamelCase(true);
            String resource="mapper/projectplan/ProjectPlanProjectionMapper.xml";
            try (var xml=getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
            var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(ProjectPlanProjectionMapper.class);
            var transaction=new TransactionTemplate(new DataSourceTransactionManager(database));
            var original=jdbc.queryForList("SELECT * FROM proj_project_milestone ORDER BY id");
            Runnable swap=() -> {
                assertEquals(1,mapper.milestoneCodeForRename(token(1L,3)));
                assertEquals(1,mapper.milestoneCodeForRename(token(2L,3)));
                assertEquals(1,mapper.updateMilestoneDefinition(update(1L,"M2")));
                assertEquals(1,mapper.updateMilestoneDefinition(update(2L,"M1")));
            };
            assertThrows(IllegalStateException.class,() -> transaction.executeWithoutResult(status -> {swap.run(); throw new IllegalStateException("Outbox write failed");}));
            assertEquals(original,jdbc.queryForList("SELECT * FROM proj_project_milestone ORDER BY id"));
            transaction.executeWithoutResult(status -> {
                assertEquals(List.of(1L,2L),mapper.selectMilestonesForUpdate(new ProjectPlanScopeQuery(7L,80L)).stream().map(ProjectMilestoneInstanceDO::getId).toList());
                swap.run();
                assertEquals(0,mapper.retirePendingMilestone(token(1L,4)));
                assertEquals(0,mapper.updateMilestoneDefinition(update(1L,"STALE")));
                assertEquals(0,mapper.retirePendingMilestone(token(3L,3))); assertEquals(0,mapper.retirePendingMilestone(token(4L,3)));
                assertEquals(1,mapper.retirePendingMilestone(token(2L,4)));
            });
            assertEquals("ACHIEVED",jdbc.queryForObject("SELECT status FROM proj_project_milestone WHERE id=1",String.class));
            assertEquals(501L,jdbc.queryForObject("SELECT source_definition_id FROM proj_project_milestone WHERE id=1",Long.class));
            assertEquals("M1",jdbc.queryForObject("SELECT milestone_code FROM proj_project_milestone WHERE id=2",String.class));
            jdbc.execute("INSERT INTO proj_project_milestone(id,tenant_id,project_id,milestone_code,status,version,deleted) VALUES (5,7,80,'M1','PENDING',0,0)");
            assertEquals(original.get(2),jdbc.queryForMap("SELECT * FROM proj_project_milestone WHERE id=3"));
            assertEquals(original.get(3),jdbc.queryForMap("SELECT * FROM proj_project_milestone WHERE id=4"));
        }
    }
    private ProjectPlanProjectionMapper.NodeProjectionChange token(Long id,int version) {return new ProjectPlanProjectionMapper.NodeProjectionChange(7L,80L,id,version,"9");}
    private ProjectPlanProjectionMapper.MilestoneDefinitionUpdate update(Long id,String code) {
        var desired=new ProjectMilestoneInstanceDO(); desired.setMilestoneCode(code); desired.setName("new name"); desired.setStageCode("CUSTOM");
        return new ProjectPlanProjectionMapper.MilestoneDefinitionUpdate(7L,80L,id,3,desired,"9");
    }
}
