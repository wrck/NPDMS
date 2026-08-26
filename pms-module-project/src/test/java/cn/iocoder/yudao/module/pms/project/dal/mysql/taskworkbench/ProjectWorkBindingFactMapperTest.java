package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLookupQuery;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectWorkBindingFactMapperTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectWorkBindingFactMapperTest {

    @Resource
    private ProjectWorkBindingFactMapper mapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private TransactionTemplate transactionTemplate;

    private long projectId;
    private long taskId;
    private long contractId;
    private long stateMachineRevisionId;
    private long templateDefinitionId;
    private int sourceDefinitionVersion;
    private String bindingSnapshot;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 972_000_000_000L + seed * 10L;
        taskId = projectId + 1;
        contractId = projectId + 2;
        stateMachineRevisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM proj_task_state_machine_revision WHERE tenant_id=0 "
                        + "AND status='PUBLISHED' ORDER BY revision_no DESC LIMIT 1", Long.class);
        Map<String, Object> definition = jdbcTemplate.queryForMap(
                "SELECT d.id,d.definition_version,d.binding_config "
                        + "FROM proj_project_template_task_definition d "
                        + "JOIN proj_project_template_revision r ON r.tenant_id=d.tenant_id "
                        + "AND r.id=d.template_revision_id "
                        + "WHERE d.tenant_id=0 AND r.status='DRAFT' "
                        + "AND d.target_context_code='SOL' "
                        + "AND d.target_object_type='SITE_SURVEY_PREPARATION' "
                        + "AND d.target_object_key='PRE_02_SITE_SURVEY' ORDER BY d.id LIMIT 1");
        templateDefinitionId = ((Number) definition.get("id")).longValue();
        sourceDefinitionVersion = ((Number) definition.get("definition_version")).intValue();
        bindingSnapshot = definition.get("binding_config").toString();
        insertProject();
        insertTaskAndContract(taskId, contractId, "BUSINESS_OBJECT", "SOL",
                "SITE_SURVEY_PREPARATION", "PRE_02_SITE_SURVEY");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                statement.executeUpdate("DELETE FROM proj_project_task_execution_contract WHERE tenant_id=0 "
                        + "AND project_task_id IN (SELECT id FROM proj_project_task WHERE project_id=" + projectId + ")");
                statement.executeUpdate("DELETE FROM proj_project_task WHERE tenant_id=0 AND project_id=" + projectId);
                statement.executeUpdate("DELETE FROM proj_project WHERE tenant_id=0 AND id=" + projectId);
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            }
            return null;
        });
    }

    @Test
    void selectsOnlyTheExactCurrentPreparationContractAndExposesAmbiguity() {
        List<ProjectWorkBindingFactRecord> facts = mapper.selectCurrentFacts(lookup(0L));
        assertEquals(1, facts.size());
        assertEquals(taskId, facts.getFirst().projectTaskId());
        assertEquals(contractId, facts.getFirst().executionContractId());
        assertEquals(templateDefinitionId, facts.getFirst().templateTaskDefinitionId());

        insertTaskAndContract(taskId + 10, contractId + 10, "TASK_NATIVE", null, null, null);
        assertEquals(1, mapper.selectCurrentFacts(lookup(0L)).size());

        insertTaskAndContract(taskId + 20, contractId + 20, "BUSINESS_OBJECT", "SOL",
                "SITE_SURVEY_PREPARATION", "PRE_02_SITE_SURVEY");
        assertEquals(2, mapper.selectCurrentFacts(lookup(0L)).size());
        assertTrue(mapper.selectCurrentFacts(lookup(1L)).isEmpty());
    }

    @Test
    void locksTheProjectTaskAndItsCurrentExecutionContract() {
        transactionTemplate.executeWithoutResult(status -> {
            ProjectWorkBindingFactLockQuery query = new ProjectWorkBindingFactLockQuery(0L, projectId, taskId);
            var task = mapper.selectProjectTaskForUpdate(query);
            var contract = mapper.selectCurrentContractForUpdate(query);
            assertNotNull(task);
            assertNotNull(contract);
            assertEquals(projectId, task.getProjectId());
            assertEquals(contractId, contract.getId());
            assertEquals(1, contract.getContractVersion());
        });
    }

    private ProjectWorkBindingFactLookupQuery lookup(long tenantId) {
        return new ProjectWorkBindingFactLookupQuery(tenantId, projectId, "BUSINESS_OBJECT", "SOL",
                "SITE_SURVEY_PREPARATION", "PRE_02_SITE_SURVEY");
    }

    private void insertProject() {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S1','ACTIVE','S1','UNASSIGNED',0,0,4,0)",
                projectId, "FSOL2-T2-" + projectId, projectId, 0,
                "F-SOL-002 Task2 " + projectId, projectId, "/", 0, 0);
    }

    private void insertTaskAndContract(long newTaskId, long newContractId, String bindingType,
                                       String targetContext, String targetObjectType, String targetObjectKey) {
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,root_task_id,tree_depth,state_machine_revision_id,"
                        + "stage_code,sort_order,source_definition_id,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,0,?,'S1',0,?,'PENDING_ASSIGN',2,0)",
                newTaskId, projectId, "PRE02-" + newTaskId, "PRE-02", newTaskId,
                stateMachineRevisionId, templateDefinitionId);
        String snapshot = "TASK_NATIVE".equals(bindingType) ? "{}" : bindingSnapshot;
        jdbcTemplate.update("INSERT INTO proj_project_task_execution_contract "
                        + "(id,project_task_id,template_task_definition_id,work_binding_type_code,"
                        + "target_context_code,target_object_type,target_object_key,binding_parameter_snapshot,"
                        + "permission_policy_ref,completion_rule_type_code,completion_rule_snapshot,"
                        + "source_definition_version,contract_version,effective_from,effective_to,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?, ?,1,NOW(3),NULL,0,0)",
                newContractId, newTaskId, templateDefinitionId, bindingType, targetContext,
                targetObjectType, targetObjectKey, snapshot, "PRE_02_SITE_SURVEY_DEFAULT",
                "BUSINESS_OBJECT_STATUS", "{\"requiredStatus\":\"DONE\"}", sourceDefinitionVersion);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量：" + name);
        }
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}
