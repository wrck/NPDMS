package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProjectPlanActivationPersistenceTest {
    EmbeddedDatabase database;
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    ProjectPlanActivationPersistence persistence;
    ProjectNodeExecutionMapper executions;
    final LocalDateTime activatedAt = LocalDateTime.of(2026,9,14,15,0);

    @BeforeEach void setup() throws Exception {
        // Isolated H2 only. Exercise the production mapper XML and Spring transaction enlistment.
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE proj_project (id BIGINT PRIMARY KEY,tenant_id BIGINT,active_plan_version_id BIGINT,version INT,lifecycle_status VARCHAR(30),deleted INT,updater VARCHAR(64),update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE proj_project_plan_version (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,base_plan_version_id BIGINT,status VARCHAR(16),designer_document VARCHAR(200),execution_snapshot VARCHAR(200),effective_at TIMESTAMP,closed_at TIMESTAMP,version INT,deleted INT,updater VARCHAR(64),update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE proj_project_node_execution (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,plan_version_id BIGINT,contract_id BIGINT,node_key VARCHAR(64),round_no INT,current_marker INT,status VARCHAR(16),started_at TIMESTAMP,started_plan_version_id BIGINT,submitted_at TIMESTAMP,submission_note VARCHAR(200),ended_at TIMESTAMP,result_snapshot VARCHAR(200),version INT,deleted INT,updater VARCHAR(64),update_time TIMESTAMP)");
        jdbc.execute("INSERT INTO proj_project VALUES (9,1,50,3,'ACTIVE',0,'initial',CURRENT_TIMESTAMP)");
        jdbc.execute("INSERT INTO proj_project_plan_version VALUES (50,1,9,NULL,'EFFECTIVE','old design','old rules',TIMESTAMP '2026-09-01 00:00:00',NULL,0,0,'initial',CURRENT_TIMESTAMP),(51,1,9,50,'DRAFT','new design',NULL,NULL,NULL,2,0,'editor',CURRENT_TIMESTAMP)");
        jdbc.execute("INSERT INTO proj_project_node_execution VALUES (20,1,9,50,30,'task:active',2,1,'ACTIVE',TIMESTAMP '2026-09-13 10:00:00',50,TIMESTAMP '2026-09-13 11:00:00','this rounds actual work',NULL,NULL,4,0,'worker',CURRENT_TIMESTAMP),(21,1,9,50,31,'task:done',1,1,'DONE',TIMESTAMP '2026-09-12 10:00:00',50,TIMESTAMP '2026-09-12 11:00:00','old completed work',TIMESTAMP '2026-09-12 12:00:00','immutable result',5,0,'worker',CURRENT_TIMESTAMP),(22,1,9,50,32,'task:unstarted',1,1,'PENDING',NULL,NULL,NULL,NULL,NULL,NULL,0,0,'initial',CURRENT_TIMESTAMP)");
        var configuration = new Configuration(new Environment("plan-activation",new SpringManagedTransactionFactory(),database));
        for (String mapper : List.of("ProjectPlanVersionMapper", "ProjectNodeExecutionMapper")) {
            String resource = "mapper/projectplan/"+mapper+".xml";
            try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        executions = session.getMapper(ProjectNodeExecutionMapper.class);
        persistence = new ProjectPlanActivationPersistence(session.getMapper(ProjectPlanVersionMapper.class),executions);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void close() { TenantContextHolder.clear(); database.shutdown(); }

    @Test void installsNewRulesWithoutRestartingWorkOrRewritingCompletedHistory() {
        var history = jdbc.queryForMap("SELECT * FROM proj_project_node_execution WHERE id=21");
        transaction.executeWithoutResult(status -> activate(3,2));
        assertEquals(51L,jdbc.queryForObject("SELECT active_plan_version_id FROM proj_project WHERE id=9",Long.class));
        assertEquals(4,jdbc.queryForObject("SELECT version FROM proj_project WHERE id=9",Integer.class));
        assertEquals("SUPERSEDED",value("proj_project_plan_version",50,"status"));
        assertEquals("old rules",value("proj_project_plan_version",50,"execution_snapshot"));
        assertEquals("old design",value("proj_project_plan_version",50,"designer_document"));
        assertEquals("EFFECTIVE",value("proj_project_plan_version",51,"status"));
        assertEquals("new frozen rules",value("proj_project_plan_version",51,"execution_snapshot"));
        assertEquals("new design",value("proj_project_plan_version",51,"designer_document"));
        var active = jdbc.queryForMap("SELECT * FROM proj_project_node_execution WHERE id=20");
        assertEquals(51L,active.get("PLAN_VERSION_ID")); assertEquals(50L,active.get("STARTED_PLAN_VERSION_ID"));
        assertEquals(2,active.get("ROUND_NO")); assertEquals("ACTIVE",active.get("STATUS"));
        assertEquals("this rounds actual work",active.get("SUBMISSION_NOTE")); assertNotNull(active.get("SUBMITTED_AT"));
        assertEquals(history,jdbc.queryForMap("SELECT * FROM proj_project_node_execution WHERE id=21"));
        assertNull(jdbc.queryForObject("SELECT current_marker FROM proj_project_node_execution WHERE id=22",Integer.class));
        assertEquals("PENDING",value("proj_project_node_execution",22,"status"));
    }
    @Test void staleProjectOrDraftRollsBackAllEarlierExecutionAndVersionWrites() {
        var original = jdbc.queryForList("SELECT * FROM proj_project_node_execution ORDER BY id");
        assertThrows(RuntimeException.class,()->transaction.executeWithoutResult(status -> activate(99,2)));
        assertUnchanged(original);
        assertThrows(RuntimeException.class,()->transaction.executeWithoutResult(status -> activate(3,99)));
        assertUnchanged(original);
    }
    @Test void laterAuditOrOutboxFailureRollsBackSuccessfulActivation() {
        var original = jdbc.queryForList("SELECT * FROM proj_project_node_execution ORDER BY id");
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status -> {
            activate(3,2); throw new IllegalStateException("later transactional audit failed");
        }));
        assertUnchanged(original);
    }
    @Test void finishedOrStaleExecutionTokensCannotBeRebasedOrRetired() {
        var plan = activation(3,2);
        transaction.executeWithoutResult(status -> {
            assertEquals(0,executions.rebaseUnfinishedIfCurrent(new ProjectNodeExecutionMapper.PlanRebase(1L,9L,21L,5,50L,51L,31L,31L,"1")));
            assertEquals(0,executions.retireUnstartedIfCurrent(new ProjectNodeExecutionMapper.PlanRetirement(1L,9L,20L,4,50L,"1")));
            assertEquals(0,executions.rebaseUnfinishedIfCurrent(new ProjectNodeExecutionMapper.PlanRebase(1L,9L,20L,99,50L,51L,30L,30L,"1")));
        });
        assertThrows(RuntimeException.class,()->transaction.executeWithoutResult(status -> persistence.activate(plan,
                List.of(new ProjectNodeExecutionMapper.PlanRebase(2L,9L,20L,4,50L,51L,30L,30L,"1")),List.of())));
        assertEquals("EFFECTIVE",value("proj_project_plan_version",50,"status"));
    }
    @Test void closedProjectAndCallsWithoutTransactionCannotPublish() {
        assertThrows(IllegalStateException.class,()->activate(3,2));
        jdbc.execute("UPDATE proj_project SET lifecycle_status='NORMAL_CLOSED' WHERE id=9");
        assertThrows(RuntimeException.class,()->transaction.executeWithoutResult(status -> activate(3,2)));
        assertEquals("DRAFT",value("proj_project_plan_version",51,"status"));
        assertEquals("EFFECTIVE",value("proj_project_plan_version",50,"status"));
    }
    void activate(int projectVersion,int draftVersion) {
        persistence.activate(activation(projectVersion,draftVersion),
                List.of(new ProjectNodeExecutionMapper.PlanRebase(1L,9L,20L,4,50L,51L,30L,30L,"1")),
                List.of(new ProjectNodeExecutionMapper.PlanRetirement(1L,9L,22L,0,50L,"1")));
    }
    ProjectPlanVersionMapper.Activation activation(int projectVersion,int draftVersion) {
        return new ProjectPlanVersionMapper.Activation(1L,9L,50L,51L,draftVersion,projectVersion,"new frozen rules",activatedAt,"1");
    }
    void assertUnchanged(Object original) {
        assertEquals(original,jdbc.queryForList("SELECT * FROM proj_project_node_execution ORDER BY id"));
        assertEquals("EFFECTIVE",value("proj_project_plan_version",50,"status"));
        assertEquals("DRAFT",value("proj_project_plan_version",51,"status"));
        assertEquals(50L,jdbc.queryForObject("SELECT active_plan_version_id FROM proj_project WHERE id=9",Long.class));
    }
    String value(String table,long id,String column) {
        // Test-only fixed identifiers; production queries are exclusively in Mapper XML.
        return jdbc.queryForObject("SELECT "+column+" FROM "+table+" WHERE id=?",String.class,id);
    }
}
