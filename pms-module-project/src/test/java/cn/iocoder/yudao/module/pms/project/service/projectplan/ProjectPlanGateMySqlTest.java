package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** V242 and production gate commands target only these connection-local TEMPORARY tables. */
@EnabledIfSystemProperty(named="pms.gate.plan.mysql",matches="true")
class ProjectPlanGateMySqlTest {
    @Test void referenceHistoryAndResultInvalidationCommitOrRollbackTogether() throws Exception {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME")); assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        try (var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"))) {
            var database=new SingleConnectionDataSource(connection,true); var jdbc=new JdbcTemplate(database);
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_gate (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,gate_code VARCHAR(64),name VARCHAR(128),stage_code VARCHAR(32),gate_type VARCHAR(16),description VARCHAR(500),validation_summary VARCHAR(1000),source_definition_id BIGINT,status VARCHAR(32),version INT,deleted BIT,updater VARCHAR(64),update_time DATETIME)");
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_gate_reference (id BIGINT PRIMARY KEY,tenant_id BIGINT,gate_id BIGINT,ref_type VARCHAR(16),ref_code VARCHAR(64),ref_version VARCHAR(32),version INT,deleted BIT,updater VARCHAR(64),update_time DATETIME,UNIQUE KEY uk_proj_gate_ref(tenant_id,gate_id,ref_type,ref_code))");
            jdbc.execute("INSERT INTO proj_project_gate(id,tenant_id,project_id,gate_code,name,stage_code,gate_type,source_definition_id,status,version,deleted) VALUES (1,7,80,'G1','gate','PREP','EXIT',501,'PASSED',3,0),(2,8,80,'G1','foreign','PREP','EXIT',502,'PASSED',3,0)");
            jdbc.execute("INSERT INTO proj_project_gate_reference(id,tenant_id,gate_id,ref_type,ref_code,ref_version,version,deleted) VALUES (20,7,1,'PROCESS','P1','1',2,0),(30,8,2,'PROCESS','P1','1',2,0)");
            var migration=Path.of("../sql/migrations/V242__project_plan_active_gate_references.sql");
            if (!Files.exists(migration)) migration=Path.of("sql/migrations/V242__project_plan_active_gate_references.sql");
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(migration));
            var configuration=new Configuration(new Environment("temporary-gates",new SpringManagedTransactionFactory(),database));
            for (String resource:List.of("mapper/projectplan/ProjectPlanProjectionMapper.xml","mapper/projectmanual/ProjectGateInstanceMapper.xml")) {
                try (var xml=getClass().getClassLoader().getResourceAsStream(resource)) {
                    assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
                }
            }
            var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
            var projections=session.getMapper(ProjectPlanProjectionMapper.class); var gates=session.getMapper(ProjectGateInstanceMapper.class);
            var transaction=new TransactionTemplate(new DataSourceTransactionManager(database));
            var oldGates=jdbc.queryForList("SELECT * FROM proj_project_gate ORDER BY id");
            var oldRefs=jdbc.queryForList("SELECT * FROM proj_project_gate_reference ORDER BY id");
            var definition=new ProjectGateInstanceDO(); definition.setGateCode("G1"); definition.setName("gate");
            definition.setStageCode("PREP"); definition.setGateType("EXIT"); definition.setValidationSummary("PROCESS:P1");
            Runnable change=() -> {
                assertEquals(1,projections.updateGateDefinition(new ProjectPlanProjectionMapper.GateDefinitionUpdate(7L,80L,1L,3,definition,"9")));
                assertEquals(1,projections.retireGateReference(new ProjectPlanProjectionMapper.GateReferenceRetirement(7L,80L,1L,20L,2,"9")));
                jdbc.execute("INSERT INTO proj_project_gate_reference(id,tenant_id,gate_id,ref_type,ref_code,ref_version,version,deleted) VALUES (21,7,1,'PROCESS','P1','2',0,0)");
                assertEquals(1,gates.updateStatusIfMatch(new ProjectGateStatusUpdate(7L,1L,4,"PASSED","PENDING","9")));
            };
            assertThrows(IllegalStateException.class,() -> transaction.executeWithoutResult(status -> {change.run(); throw new IllegalStateException("audit failed");}));
            assertEquals(oldGates,jdbc.queryForList("SELECT * FROM proj_project_gate ORDER BY id"));
            assertEquals(oldRefs,jdbc.queryForList("SELECT * FROM proj_project_gate_reference ORDER BY id"));
            transaction.executeWithoutResult(status -> change.run());
            assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM proj_project_gate WHERE id=1",String.class));
            assertEquals(5,jdbc.queryForObject("SELECT version FROM proj_project_gate WHERE id=1",Integer.class));
            assertEquals(501L,jdbc.queryForObject("SELECT source_definition_id FROM proj_project_gate WHERE id=1",Long.class));
            assertEquals("1",jdbc.queryForObject("SELECT ref_version FROM proj_project_gate_reference WHERE id=20",String.class));
            assertEquals(1,jdbc.queryForObject("SELECT deleted FROM proj_project_gate_reference WHERE id=20",Integer.class));
            assertEquals(0,projections.retireGateReference(new ProjectPlanProjectionMapper.GateReferenceRetirement(7L,99L,1L,21L,0,"9")));
            assertEquals(0,projections.retireGateReference(new ProjectPlanProjectionMapper.GateReferenceRetirement(8L,80L,1L,21L,0,"9")));
            assertEquals(0,projections.retireGateReference(new ProjectPlanProjectionMapper.GateReferenceRetirement(7L,80L,1L,21L,9,"9")));
            transaction.executeWithoutResult(status -> {
                assertEquals(1,projections.retireGateReference(new ProjectPlanProjectionMapper.GateReferenceRetirement(7L,80L,1L,21L,0,"9")));
                jdbc.execute("INSERT INTO proj_project_gate_reference(id,tenant_id,gate_id,ref_type,ref_code,ref_version,version,deleted) VALUES (22,7,1,'PROCESS','P1','3',0,0)");
            });
            assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_gate_reference WHERE tenant_id=7",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_gate_reference WHERE tenant_id=7 AND deleted=0",Integer.class));
            assertEquals(oldGates.get(1),jdbc.queryForMap("SELECT * FROM proj_project_gate WHERE id=2"));
            assertEquals(oldRefs.get(1),jdbc.queryForMap("SELECT * FROM proj_project_gate_reference WHERE id=30"));
        }
    }
}
