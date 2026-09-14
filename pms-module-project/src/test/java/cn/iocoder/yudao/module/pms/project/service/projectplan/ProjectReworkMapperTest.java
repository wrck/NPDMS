package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectReworkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.*;
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

class ProjectReworkMapperTest {
    @Test void reworkRollsBackAsOneUnitAndKeepsOldResultsAndUnselectedTaskUntouched() throws Exception {
        // Real SQL and Spring transaction enlistment, isolated in-memory; no application/Docker configuration.
        var database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc=new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE proj_project (id BIGINT PRIMARY KEY,tenant_id BIGINT,active_plan_version_id BIGINT,version INT,lifecycle_status VARCHAR(30),deleted INT,updater VARCHAR(64),update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE proj_project_task (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,status VARCHAR(30),actual_start_time TIMESTAMP,actual_end_time TIMESTAMP,progress INT,version INT,deleted INT,updater VARCHAR(64),update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE proj_project_node_execution (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,node_key VARCHAR(64),version INT,current_marker INT,status VARCHAR(30),deleted INT,result_snapshot VARCHAR(100),submitted_at TIMESTAMP,UNIQUE(tenant_id,project_id,node_key,current_marker))");
            jdbc.execute("CREATE TABLE proj_project_task_execution_contract (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_task_id BIGINT,current_marker INT GENERATED ALWAYS AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END),effective_to TIMESTAMP,version INT,deleted INT,binding_parameter_snapshot VARCHAR(200),permission_snapshot VARCHAR(200),approval_instance_id BIGINT,updater VARCHAR(64),update_time TIMESTAMP,UNIQUE(tenant_id,project_task_id,current_marker))");
            jdbc.execute("CREATE TABLE proj_project_stage_execution_contract (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,stage_id BIGINT,effective_to TIMESTAMP,version INT,deleted INT,binding_snapshot VARCHAR(200),permission_snapshot VARCHAR(200),updater VARCHAR(64),update_time TIMESTAMP)");
            jdbc.execute("INSERT INTO proj_project VALUES (9,1,50,3,'ACTIVE',0,'original',CURRENT_TIMESTAMP)");
            jdbc.execute("INSERT INTO proj_project_task VALUES (2,1,9,'DONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,100,5,0,'original',CURRENT_TIMESTAMP),(3,1,9,'DONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,100,7,0,'other',CURRENT_TIMESTAMP)");
            jdbc.execute("INSERT INTO proj_project_node_execution VALUES (20,1,9,'t1',1,1,'DONE',0,'immutable result',CURRENT_TIMESTAMP)");
            jdbc.execute("INSERT INTO proj_project_task_execution_contract (id,tenant_id,project_task_id,version,deleted,binding_parameter_snapshot,permission_snapshot,approval_instance_id,updater) VALUES (30,1,2,4,0,'old binding','old permission',77,'original')");
            jdbc.execute("INSERT INTO proj_project_stage_execution_contract VALUES (40,1,9,1,NULL,2,0,'old stage binding','old stage permission','original',CURRENT_TIMESTAMP)");
            var oldTaskContract=jdbc.queryForMap("SELECT * FROM proj_project_task_execution_contract WHERE id=30");
            var oldStageContract=jdbc.queryForMap("SELECT * FROM proj_project_stage_execution_contract WHERE id=40");
            var oldSubmitted=jdbc.queryForObject("SELECT submitted_at FROM proj_project_node_execution WHERE id=20",java.sql.Timestamp.class);
            var configuration=new Configuration(new Environment("rework",new SpringManagedTransactionFactory(),database));
            try (var xml=getClass().getClassLoader().getResourceAsStream("mapper/projectplan/ProjectReworkMapper.xml")) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,"rework",configuration.getSqlFragments()).parse();
            }
            try (var xml=getClass().getClassLoader().getResourceAsStream("mapper/projectplan/ProjectPlanProjectionMapper.xml")) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,"plan-projection",configuration.getSqlFragments()).parse();
            }
            var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
            var mapper=session.getMapper(ProjectReworkMapper.class);
            var projections=session.getMapper(ProjectPlanProjectionMapper.class);
            var transaction=new TransactionTemplate(new DataSourceTransactionManager(database));
            Runnable change=()->{
                var now=java.time.LocalDateTime.now();
                assertEquals(1,projections.closeTaskContract(new ProjectPlanProjectionMapper.ContractClosure(1L,9L,2L,30L,4,now,"9")));
                jdbc.execute("INSERT INTO proj_project_task_execution_contract (id,tenant_id,project_task_id,version,deleted,binding_parameter_snapshot,permission_snapshot,updater) VALUES (31,1,2,0,0,'current plan binding','current plan permission','9')");
                assertEquals(1,projections.closeStageContract(new ProjectPlanProjectionMapper.ContractClosure(1L,9L,1L,40L,2,now,"9")));
                jdbc.execute("INSERT INTO proj_project_stage_execution_contract VALUES (41,1,9,1,NULL,0,0,'current stage binding','current stage permission','9',CURRENT_TIMESTAMP)");
                assertEquals(1,mapper.resetTaskProjection(new ProjectReworkTaskReset(1L,9L,2L,5,"DONE","PENDING_ASSIGN","9")));
                assertEquals(1,mapper.retireEndedExecution(new ProjectExecutionRetire(1L,9L,20L,1)));
                jdbc.execute("INSERT INTO proj_project_node_execution VALUES (21,1,9,'t1',0,1,'PENDING',0,NULL,NULL)");
                assertEquals(1,mapper.advanceProjectVersion(new ProjectReworkVersionUpdate(1L,9L,50L,3,"9")));
            };
            assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->{ change.run(); throw new IllegalStateException("later write failed"); }));
            assertEquals("DONE",jdbc.queryForObject("SELECT status FROM proj_project_task WHERE id=2",String.class));
            assertEquals(1,jdbc.queryForObject("SELECT current_marker FROM proj_project_node_execution WHERE id=20",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_node_execution WHERE id=21",Integer.class));
            assertEquals(oldTaskContract,jdbc.queryForMap("SELECT * FROM proj_project_task_execution_contract WHERE id=30"));
            assertEquals(oldStageContract,jdbc.queryForMap("SELECT * FROM proj_project_stage_execution_contract WHERE id=40"));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_task_execution_contract WHERE id=31",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_stage_execution_contract WHERE id=41",Integer.class));
            transaction.executeWithoutResult(status->change.run());
            assertEquals("PENDING_ASSIGN",jdbc.queryForObject("SELECT status FROM proj_project_task WHERE id=2",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT progress FROM proj_project_task WHERE id=2",Integer.class));
            assertNull(jdbc.queryForObject("SELECT current_marker FROM proj_project_node_execution WHERE id=20",Integer.class));
            assertEquals("immutable result",jdbc.queryForObject("SELECT result_snapshot FROM proj_project_node_execution WHERE id=20",String.class));
            assertEquals(oldSubmitted,jdbc.queryForObject("SELECT submitted_at FROM proj_project_node_execution WHERE id=20",java.sql.Timestamp.class));
            assertEquals(1,jdbc.queryForObject("SELECT version FROM proj_project_node_execution WHERE id=20",Integer.class));
            assertEquals("DONE",jdbc.queryForObject("SELECT status FROM proj_project_task WHERE id=3",String.class));
            assertEquals(7,jdbc.queryForObject("SELECT version FROM proj_project_task WHERE id=3",Integer.class));
            assertEquals("old binding",jdbc.queryForObject("SELECT binding_parameter_snapshot FROM proj_project_task_execution_contract WHERE id=30",String.class));
            assertEquals("old permission",jdbc.queryForObject("SELECT permission_snapshot FROM proj_project_task_execution_contract WHERE id=30",String.class));
            assertEquals(77L,jdbc.queryForObject("SELECT approval_instance_id FROM proj_project_task_execution_contract WHERE id=30",Long.class));
            assertNull(jdbc.queryForObject("SELECT approval_instance_id FROM proj_project_task_execution_contract WHERE id=31",Long.class));
            assertEquals(31L,jdbc.queryForObject("SELECT id FROM proj_project_task_execution_contract WHERE current_marker=1",Long.class));
            assertEquals("old stage binding",jdbc.queryForObject("SELECT binding_snapshot FROM proj_project_stage_execution_contract WHERE id=40",String.class));
            assertEquals("old stage permission",jdbc.queryForObject("SELECT permission_snapshot FROM proj_project_stage_execution_contract WHERE id=40",String.class));
            assertEquals(41L,jdbc.queryForObject("SELECT id FROM proj_project_stage_execution_contract WHERE effective_to IS NULL",Long.class));
            assertEquals(0,mapper.advanceProjectVersion(new ProjectReworkVersionUpdate(2L,9L,50L,4,"9")));
            assertEquals(0,mapper.retireEndedExecution(new ProjectExecutionRetire(1L,9L,21L,0)));
        } finally { database.shutdown(); }
    }
}
