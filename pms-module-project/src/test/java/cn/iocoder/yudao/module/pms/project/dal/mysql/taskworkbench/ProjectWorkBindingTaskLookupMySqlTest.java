package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApiImpl;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
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
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Actual API + Mapper XML against one connection's temporary tables; permanent project data is never touched. */
@EnabledIfSystemProperty(named="pms.work-binding.task.mysql",matches="true")
class ProjectWorkBindingTaskLookupMySqlTest {
    @Test void sameOwnerTargetsStayTaskScopedAcrossContractRenewalAndTenantBoundaries() throws Exception {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME")); assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"))) {
            var database = new SingleConnectionDataSource(connection,true);
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TEMPORARY TABLE proj_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,version INT,lifecycle_template_id BIGINT,lifecycle_template_revision_id BIGINT,lifecycle_template_revision_no INT,deleted BIT DEFAULT 0)");
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_task(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,version INT,deleted BIT DEFAULT 0)");
            jdbc.execute("CREATE TEMPORARY TABLE proj_project_task_execution_contract(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_task_id BIGINT,work_binding_type_code VARCHAR(32),target_context_code VARCHAR(32),target_object_type VARCHAR(64),target_object_key VARCHAR(64),binding_parameter_snapshot TEXT,source_definition_version INT,contract_version INT,effective_to DATETIME,current_marker INT,deleted BIT DEFAULT 0)");
            jdbc.execute("INSERT INTO proj_project(id,tenant_id,version,lifecycle_template_id,lifecycle_template_revision_id,lifecycle_template_revision_no) VALUES(9,1,4,70,71,1),(10,2,4,70,71,1)");
            jdbc.execute("INSERT INTO proj_project_task(id,tenant_id,project_id,version) VALUES(20,1,9,3),(21,1,9,3),(22,2,10,3)");
            String binding = "{\"schemaVersion\":2,\"dynamicFormTemplateId\":700,\"dynamicFormTemplateRevisionId\":701,\"dynamicFormRevisionNo\":3,\"dynamicFormRevisionFactVersion\":9}";
            for (int task=20;task<=22;task++) insert(jdbc,task+10,task==22?2:1,task,binding);
            var configuration = new Configuration(new Environment("task-binding-temporary",new SpringManagedTransactionFactory(),database));
            String resource = "mapper/taskworkbench/ProjectWorkBindingFactMapper.xml";
            try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
            var mapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(ProjectWorkBindingFactMapper.class);
        var api = new ProjectWorkBindingFactApiImpl(mock(ProjectMasterMapper.class),mapper,
                mock(cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper.class),
                mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi.class));
            TenantContextHolder.setTenantId(1L);
            try {
                assertThrows(ServiceException.class,()->api.inspect(new ProjectWorkBindingFactQuery(9L,ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS)));
                assertEquals(30L,api.inspectTask(query(9L,20L)).executionContractId());
                assertEquals(31L,api.inspectTask(query(9L,21L)).executionContractId());
                assertThrows(ServiceException.class,()->api.inspectTask(query(9L,22L)));
                assertThrows(ServiceException.class,()->api.inspectTask(new ProjectWorkBindingTaskFactQuery(9L,20L,ProjectWorkBindingTarget.SITE_SURVEY_PREPARATION)));
                jdbc.execute("UPDATE proj_project_task_execution_contract SET effective_to='2026-09-14 15:00:00',current_marker=NULL WHERE id=30");
                insert(jdbc,33,1,20,binding);
                assertEquals(33L,api.inspectTask(query(9L,20L)).executionContractId());
                assertEquals(31L,api.inspectTask(query(9L,21L)).executionContractId());
                TenantContextHolder.setTenantId(2L);
                assertThrows(ServiceException.class,()->api.inspectTask(query(9L,20L)));
                assertEquals(32L,api.inspectTask(query(10L,22L)).executionContractId());
            } finally { TenantContextHolder.clear(); }
        }
    }
    private ProjectWorkBindingTaskFactQuery query(Long project,Long task) { return new ProjectWorkBindingTaskFactQuery(project,task,ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS); }
    private void insert(JdbcTemplate jdbc,long id,long tenant,long task,String binding) {
        jdbc.update("INSERT INTO proj_project_task_execution_contract(id,tenant_id,project_task_id,work_binding_type_code,target_context_code,target_object_type,target_object_key,binding_parameter_snapshot,source_definition_version,contract_version,current_marker) VALUES(?,?,?,'BUSINESS_OBJECT','SOL','REQUIREMENT_ANALYSIS','PRE_04_REQUIREMENT_ANALYSIS',?,2,1,1)",id,tenant,task,binding);
    }
}
