package cn.iocoder.yudao.module.pms.engineering.preparation;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationCommand;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormCatalog;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormCatalogProvider;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationInitializationService;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApiImpl;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = PreparationInitializationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PreparationInitializationMySqlIntegrationTest {

    @Resource PreparationInitializationService service;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource TransactionTemplate transactionTemplate;

    private long projectId;
    private long taskId;
    private long contractId;
    private long stateMachineRevisionId;
    private long templateDefinitionId;
    private int sourceDefinitionVersion;
    private String bindingSnapshot;
    private String idempotencyKey;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8&nullCatalogMeansCurrent=true");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 978_000_000_000L + seed * 10L;
        taskId = projectId + 1;
        contractId = projectId + 2;
        idempotencyKey = "PRE02_INIT:" + projectId + ":" + contractId + ":1";
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
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                statement.executeUpdate("DELETE FROM sol_dynamic_form_instance WHERE tenant_id=0 "
                        + "AND preparation_id IN (SELECT id FROM sol_preparation WHERE project_id=" + projectId + ")");
                statement.executeUpdate("DELETE FROM sol_preparation_item WHERE tenant_id=0 "
                        + "AND preparation_id IN (SELECT id FROM sol_preparation WHERE project_id=" + projectId + ")");
                statement.executeUpdate("DELETE FROM sol_preparation WHERE tenant_id=0 AND project_id=" + projectId);
                statement.executeUpdate("DELETE FROM proj_project_task_execution_contract WHERE tenant_id=0 "
                        + "AND project_task_id=" + taskId);
                statement.executeUpdate("DELETE FROM proj_project_task WHERE tenant_id=0 AND project_id=" + projectId);
                statement.executeUpdate("DELETE FROM proj_project WHERE tenant_id=0 AND id=" + projectId);
                statement.executeUpdate("DELETE FROM plt_operation_audit WHERE tenant_id=0 "
                        + "AND correlation_id='PRE02-IT-" + projectId + "'");
                statement.executeUpdate("DELETE FROM plt_idempotency_record WHERE tenant_id=0 "
                        + "AND scope_code='PREPARATION_INITIALIZE' AND actor_id=9 "
                        + "AND idempotency_key='" + idempotencyKey + "'");
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            }
            return null;
        });
        TenantContextHolder.clear();
    }

    @Test
    void outerProjectTransactionCommitsProjectSolAndPlatformFactsTogether() {
        var result = transactionTemplate.execute(status -> {
            insertProjectTaskAndContract();
            return service.initialize(command());
        });

        assertEquals(projectId, result.projectId());
        assertEquals(1L, count("proj_project", "id", projectId));
        assertEquals(1L, count("proj_project_task", "id", taskId));
        assertEquals(1L, count("proj_project_task_execution_contract", "id", contractId));
        assertEquals(1L, count("sol_preparation", "project_id", projectId));
        assertEquals(5L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_item i "
                + "JOIN sol_preparation p ON p.tenant_id=i.tenant_id AND p.id=i.preparation_id "
                + "WHERE p.tenant_id=0 AND p.project_id=?", Long.class, projectId));
        assertEquals(5L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_dynamic_form_instance f "
                + "JOIN sol_preparation p ON p.tenant_id=f.tenant_id AND p.id=f.preparation_id "
                + "WHERE p.tenant_id=0 AND p.project_id=?", Long.class, projectId));
        assertEquals(1L, completedIdempotencyCount());
        assertEquals(1L, successAuditCount());
    }

    @Test
    void failureAfterSolWriteRollsBackProjectSolAndPlatformFactsTogether() {
        assertThrows(ForcedRollback.class, () -> transactionTemplate.executeWithoutResult(status -> {
            insertProjectTaskAndContract();
            service.initialize(command());
            throw new ForcedRollback();
        }));

        assertEquals(0L, count("proj_project", "id", projectId));
        assertEquals(0L, count("proj_project_task", "id", taskId));
        assertEquals(0L, count("proj_project_task_execution_contract", "id", contractId));
        assertEquals(0L, count("sol_preparation", "project_id", projectId));
        assertEquals(0L, completedIdempotencyCount());
        assertEquals(0L, successAuditCount());
    }

    private void insertProjectTaskAndContract() {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S1','ACTIVE','S1','UNASSIGNED',0,0,4,0)",
                projectId, "FSOL2-T4-" + projectId, projectId, 0,
                "F-SOL-002 Task4 " + projectId, projectId, "/", 0, 0);
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,root_task_id,tree_depth,state_machine_revision_id,"
                        + "stage_code,sort_order,source_definition_id,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,0,?,'S1',0,?,'PENDING_ASSIGN',2,0)",
                taskId, projectId, "PRE02-" + taskId, "PRE-02", taskId,
                stateMachineRevisionId, templateDefinitionId);
        jdbcTemplate.update("INSERT INTO proj_project_task_execution_contract "
                        + "(id,project_task_id,template_task_definition_id,work_binding_type_code,"
                        + "target_context_code,target_object_type,target_object_key,binding_parameter_snapshot,"
                        + "permission_policy_ref,completion_rule_type_code,completion_rule_snapshot,"
                        + "source_definition_version,contract_version,effective_from,effective_to,version,tenant_id) "
                        + "VALUES (?,?,?,'BUSINESS_OBJECT','SOL','SITE_SURVEY_PREPARATION',"
                        + "'PRE_02_SITE_SURVEY',?,'PRE_02_SITE_SURVEY_DEFAULT','BUSINESS_OBJECT_STATUS',"
                        + "'{\"requiredStatus\":\"DONE\"}',?,1,NOW(3),NULL,0,0)",
                contractId, taskId, templateDefinitionId, bindingSnapshot, sourceDefinitionVersion);
    }

    private PreparationInitializationCommand command() {
        return new PreparationInitializationCommand(projectId, taskId, contractId,
                4, 2, 1, PreparationInitializationApi.TRIGGER_PROJECT_CREATION,
                idempotencyKey, "PRE02-IT-" + projectId, 9L);
    }

    private long count(String table, String column, long value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE tenant_id=0 AND " + column + "=?", Long.class, value);
    }

    private long completedIdempotencyCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code='PREPARATION_INITIALIZE' AND actor_id=9 "
                + "AND idempotency_key=? AND status='COMPLETED'", Long.class, idempotencyKey);
    }

    private long successAuditCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='PREPARATION_INITIALIZE' "
                + "AND correlation_id=? AND result_code='SUCCESS'", Long.class,
                "PRE02-IT-" + projectId);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    static class ForcedRollback extends RuntimeException {
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = {PreparationMapper.class, DynamicFormInstanceMapper.class,
            ProjectMasterMapper.class, ProjectWorkBindingFactMapper.class,
            PlatformIdempotencyRecordMapper.class})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PreparationInitializationService.class, ProjectWorkBindingFactApiImpl.class,
            PlatformCommandExecutionApiImpl.class, OperationAuditApiImpl.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        FixedSurveyFormCatalogProvider catalogProvider() {
            FixedSurveyFormCatalogProvider provider = mock(FixedSurveyFormCatalogProvider.class);
            when(provider.load()).thenReturn(catalog());
            return provider;
        }

        @Bean ProjectParticipantFactApi participantFactApi() {
            return mock(ProjectParticipantFactApi.class);
        }

        @Bean ProjectScopeApi projectScopeApi() {
            return mock(ProjectScopeApi.class);
        }

        @Bean PermissionApi permissionApi() {
            return mock(PermissionApi.class);
        }

        private static FixedSurveyFormCatalog catalog() {
            List<String> forms = List.of("POWER", "NETWORK_PORT", "FIBER", "CABINET",
                    "NETWORK_CABLE", "OPTICAL_MODULE");
            return new FixedSurveyFormCatalog(1, "PRE_02_SITE_SURVEY", 1,
                    List.of(new FixedSurveyFormCatalog.FieldDefinition(
                            "siteCondition", "TEXT", true, 200, List.of(), 1)),
                    forms.stream().map(code -> new FixedSurveyFormCatalog.FormDefinition(code, 1)).toList());
        }
    }
}
