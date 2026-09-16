package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** All table names below shadow permanent tables on this ONE connection. Closing it removes only temporary fixtures. */
@EnabledIfSystemProperty(named="pms.plan.projection.mysql",matches="true")
class ProjectPlanProjectionMySqlTest {
    @Test void activeCodeMigrationAndProjectionSqlPreserveHistoryWorkAndTenantBoundaries() throws Exception {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME")); assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"))) {
            var database = new SingleConnectionDataSource(connection,true);
            var jdbc = new JdbcTemplate(database);
            String common = "id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,project_id BIGINT NOT NULL,deleted BIT NOT NULL DEFAULT 0,version INT DEFAULT 0,updater VARCHAR(64),update_time DATETIME(3)";
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_stage ("+common+",stage_code VARCHAR(32) NOT NULL,name VARCHAR(64) DEFAULT 'fixture',sort_order INT DEFAULT 0,entry_criteria TEXT,exit_criteria TEXT,start_node BOOL,terminal_node BOOL,status VARCHAR(16) DEFAULT 'PENDING',UNIQUE KEY uk_proj_stage(tenant_id,project_id,stage_code))");
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_task ("+common+",task_code VARCHAR(64) NOT NULL,name VARCHAR(64) DEFAULT 'fixture',stage_code VARCHAR(32),parent_task_code VARCHAR(64),parent_task_id BIGINT,root_task_id BIGINT,tree_depth INT,priority INT DEFAULT 2,sort_order INT DEFAULT 0,estimated_hours DECIMAL(10,2),description TEXT,satisfaction_timing VARCHAR(32),acc_satisfaction_template_id BIGINT,template_revision_id BIGINT,template_version INT,satisfaction_rule_version VARCHAR(64),satisfaction_threshold DECIMAL(10,2),status VARCHAR(16) DEFAULT 'PENDING_ASSIGN',actual_start_time DATETIME(3),actual_end_time DATETIME(3),progress INT,UNIQUE KEY uk_proj_task(tenant_id,project_id,task_code))");
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_milestone ("+common+",milestone_code VARCHAR(64),UNIQUE KEY uk_proj_milestone(tenant_id,project_id,milestone_code))");
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_gate ("+common+",gate_code VARCHAR(64),UNIQUE KEY uk_proj_gate(tenant_id,project_id,gate_code))");
            jdbc.execute("CREATE TEMPORARY TABLE acc_project_deliverable ("+common+",deliverable_code VARCHAR(64),UNIQUE KEY uk_acc_project_deliverable(tenant_id,project_id,deliverable_code))");
            // Load the real migration, after all five names have been shadowed by temporary tables.
            var migration = Path.of("../sql/migrations/V236__project_plan_active_node_codes.sql");
            if (!Files.exists(migration)) migration = Path.of("sql/migrations/V236__project_plan_active_node_codes.sql");
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.FileSystemResource(migration));
            var codes = List.of(new CodeTable("proj_project_stage","stage_code"),new CodeTable("proj_project_task","task_code"),
                    new CodeTable("proj_project_milestone","milestone_code"),new CodeTable("proj_project_gate","gate_code"),new CodeTable("acc_project_deliverable","deliverable_code"));
            for (var table : codes) {
                String insert = "INSERT INTO "+table.table()+"(id,tenant_id,project_id,"+table.code()+",deleted) VALUES (?,?,?,? ,?)";
                jdbc.update(insert,1,1,99,"REUSED",1); jdbc.update(insert,2,1,99,"REUSED",0);
                assertThrows(DataIntegrityViolationException.class,()->jdbc.update(insert,3,1,99,"REUSED",0));
                jdbc.update("UPDATE "+table.table()+" SET deleted=1 WHERE id=2"); jdbc.update(insert,3,1,99,"REUSED",0);
                assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM "+table.table()+" WHERE "+table.code()+"='REUSED'",Integer.class));
                jdbc.update(insert,4,2,99,"REUSED",0); // Another tenant's current code is independent.
            }
            jdbc.execute("CREATE TEMPORARY TABLE proj_project (id BIGINT PRIMARY KEY,tenant_id BIGINT,task_tree_version BIGINT,lifecycle_status VARCHAR(20),deleted BIT,updater VARCHAR(64),update_time DATETIME(3))");
            jdbc.execute("CREATE TEMPORARY TABLE proj_task_tree_path (tenant_id BIGINT,project_id BIGINT,ancestor_task_id BIGINT,descendant_task_id BIGINT)");
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_task_execution_contract (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_task_id BIGINT,effective_to DATETIME(3),current_marker INT GENERATED ALWAYS AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END),version INT DEFAULT 0,deleted BIT DEFAULT 0,updater VARCHAR(64),update_time DATETIME(3),binding_parameter_snapshot VARCHAR(100))");
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_stage_execution_contract ("+common+",stage_id BIGINT,effective_to DATETIME(3),binding_snapshot VARCHAR(100))");
            jdbc.execute("INSERT INTO proj_project VALUES (9,1,0,'ACTIVE',0,NULL,NULL)");
            jdbc.execute("INSERT INTO proj_project_stage(id,tenant_id,project_id,stage_code,status) VALUES(11,1,9,'A','ACTIVE'),(12,1,9,'B','PENDING')");
            jdbc.execute("INSERT INTO proj_project_task(id,tenant_id,project_id,task_code,stage_code,status,actual_start_time,progress) VALUES(20,1,9,'T1','A','IN_PROGRESS','2026-09-13 10:00:00',65),(21,1,9,'T2','B','PENDING_ASSIGN',NULL,0)");
            jdbc.execute("INSERT INTO proj_project_task_execution_contract(id,tenant_id,project_task_id,binding_parameter_snapshot) VALUES(30,1,20,'immutable binding')");
            jdbc.execute("INSERT INTO proj_project_stage_execution_contract(id,tenant_id,project_id,stage_id,binding_snapshot) VALUES(40,1,9,11,'immutable stage binding')");
            jdbc.execute("INSERT INTO proj_task_tree_path VALUES(1,9,20,20),(1,9,21,21),(2,9,20,20),(1,10,20,20)");
            var configuration = new Configuration(new Environment("temporary-plan-projections",new SpringManagedTransactionFactory(),database));
            String resource = "mapper/projectplan/ProjectPlanProjectionMapper.xml";
            try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
            var mapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(ProjectPlanProjectionMapper.class);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
            assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status -> {
                assertEquals(1,mapper.stageCodeForRename(new ProjectPlanProjectionMapper.NodeProjectionChange(1L,9L,11L,0,"1")));
                throw new IllegalStateException("later write failed");
            }));
            assertEquals("A",jdbc.queryForObject("SELECT stage_code FROM proj_project_stage WHERE id=11",String.class));
            var started = jdbc.queryForObject("SELECT actual_start_time FROM proj_project_task WHERE id=20",java.sql.Timestamp.class);
            transaction.executeWithoutResult(status -> {
                assertEquals(0,mapper.retireUnstartedTask(new ProjectPlanProjectionMapper.NodeProjectionChange(1L,9L,20L,0,"1")));
                assertEquals(0,mapper.retireUnstartedStage(new ProjectPlanProjectionMapper.NodeProjectionChange(1L,9L,11L,0,"1")));
                for (long id : List.of(11L,12L)) assertEquals(1,mapper.stageCodeForRename(new ProjectPlanProjectionMapper.NodeProjectionChange(1L,9L,id,0,"1")));
                assertEquals(1,mapper.updateStageDefinition(new ProjectPlanProjectionMapper.StageDefinitionUpdate(1L,9L,11L,0,stage("B"),"1")));
                assertEquals(1,mapper.updateStageDefinition(new ProjectPlanProjectionMapper.StageDefinitionUpdate(1L,9L,12L,0,stage("A"),"1")));
                assertEquals(1,mapper.taskCodeForRename(new ProjectPlanProjectionMapper.NodeProjectionChange(1L,9L,20L,0,"1")));
                var task = new ProjectTaskInstanceDO(); task.setCode("RENAMED"); task.setName("新任务名"); task.setStageCode("B"); task.setRootTaskId(20L); task.setTreeDepth(0); task.setPriority(2); task.setSortOrder(0);
                task.setAccSatisfactionTemplateId(81L); task.setTemplateRevisionId(82L); task.setTemplateVersion(3);
                task.setSatisfactionRuleVersion("frozen-v2"); task.setSatisfactionThreshold(new java.math.BigDecimal("90"));
                assertEquals(1,mapper.updateTaskDefinition(new ProjectPlanProjectionMapper.TaskDefinitionUpdate(1L,9L,20L,0,task,"1")));
                assertEquals(0,mapper.updateTaskDefinition(new ProjectPlanProjectionMapper.TaskDefinitionUpdate(2L,9L,20L,1,task,"1")));
                assertEquals(1,mapper.retireUnstartedTask(new ProjectPlanProjectionMapper.NodeProjectionChange(1L,9L,21L,0,"1")));
                assertEquals(1,mapper.retireUnstartedStage(new ProjectPlanProjectionMapper.NodeProjectionChange(1L,9L,12L,1,"1")));
                var now = LocalDateTime.of(2026,9,14,15,0);
                assertEquals(1,mapper.closeTaskContract(new ProjectPlanProjectionMapper.ContractClosure(1L,9L,20L,30L,0,now,"1")));
                assertEquals(1,mapper.closeStageContract(new ProjectPlanProjectionMapper.ContractClosure(1L,9L,11L,40L,0,now,"1")));
                assertEquals(2,mapper.deleteCurrentTaskPaths(new ProjectPlanScopeQuery(1L,9L)));
                assertEquals(1,mapper.advanceTaskTreeVersion(new ProjectPlanProjectionMapper.TaskTreeVersionUpdate(1L,9L,0L,"1")));
            });
            assertEquals(started,jdbc.queryForObject("SELECT actual_start_time FROM proj_project_task WHERE id=20",java.sql.Timestamp.class));
            assertEquals(65,jdbc.queryForObject("SELECT progress FROM proj_project_task WHERE id=20",Integer.class));
            assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT status FROM proj_project_task WHERE id=20",String.class));
            assertEquals(82L,jdbc.queryForObject("SELECT template_revision_id FROM proj_project_task WHERE id=20",Long.class));
            assertEquals("frozen-v2",jdbc.queryForObject("SELECT satisfaction_rule_version FROM proj_project_task WHERE id=20",String.class));
            assertEquals("A",jdbc.queryForObject("SELECT stage_code FROM proj_project_stage WHERE id=12",String.class));
            assertNull(jdbc.queryForObject("SELECT current_marker FROM proj_project_task_execution_contract WHERE id=30",Integer.class));
            assertEquals("immutable binding",jdbc.queryForObject("SELECT binding_parameter_snapshot FROM proj_project_task_execution_contract WHERE id=30",String.class));
            assertEquals("immutable stage binding",jdbc.queryForObject("SELECT binding_snapshot FROM proj_project_stage_execution_contract WHERE id=40",String.class));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM proj_task_tree_path",Integer.class));
        }
    }
    private ProjectStageInstanceDO stage(String code) {
        var row = new ProjectStageInstanceDO(); row.setCode(code); row.setName("阶段"+code); row.setSortOrder(0); return row;
    }
    private record CodeTable(String table,String code) { }
}
