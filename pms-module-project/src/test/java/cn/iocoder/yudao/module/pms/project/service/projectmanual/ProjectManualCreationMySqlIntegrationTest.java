package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationServiceImpl;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeMetrics;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeProjectionService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeResolutionService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectTemplateMatchHistoryService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeClassificationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeSourceCorrectionService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectTemplateMatchHistoryQueryService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.TrustedProjectServicePrincipalRegistry;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.outbox.PlatformOutboxDeliveryApiImpl;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateServiceImpl;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class ProjectManualCreationMySqlIntegrationTest extends ProjectManualCreationMySqlTestSupport {

    @Test
    void successfulRootCreationPublishesInitialTreeProjection() {
        ManualProjectCreateCommand command = newCommand();
        Integer originalSortOrder = jdbcTemplate.queryForObject(
                "SELECT sort_order FROM proj_project_template_task_definition "
                        + "WHERE template_revision_id=? AND task_code='T-ASSIGN-PM'",
                Integer.class, command.templateRevisionId());
        Integer parentSortOrder = jdbcTemplate.queryForObject(
                "SELECT sort_order FROM proj_project_template_task_definition "
                        + "WHERE template_revision_id=? AND task_code='T-ASSIGN-SM'",
                Integer.class, command.templateRevisionId());
        int changed = jdbcTemplate.update("UPDATE proj_project_template_task_definition "
                        + "SET parent_task_code='T-ASSIGN-SM', sort_order=? WHERE template_revision_id=? "
                        + "AND task_code='T-ASSIGN-PM' AND parent_task_code IS NULL",
                parentSortOrder - 1, command.templateRevisionId());
        assertEquals(1, changed, "集成测试模板必须包含可组成三层树的冻结任务");
        ManualProjectCreateResult created;
        try {
            created = applicationService.create(command, newActor());
        } finally {
            jdbcTemplate.update("UPDATE proj_project_template_task_definition "
                            + "SET parent_task_code=NULL, sort_order=? "
                            + "WHERE template_revision_id=? AND task_code='T-ASSIGN-PM' "
                            + "AND parent_task_code='T-ASSIGN-SM'",
                    originalSortOrder, command.templateRevisionId());
        }

        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_tree_version WHERE root_project_id=? "
                        + "AND tree_version=1 AND status='ACTIVE' AND node_count=1 AND path_count=1",
                Long.class, created.id()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_tree_path WHERE root_project_id=? "
                        + "AND tree_version=1 AND ancestor_project_id=? AND descendant_project_id=? AND distance=0",
                Long.class, created.id(), created.id(), created.id()));
        assertEquals(3L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM proj_project_task task
                JOIN proj_task_state_machine_revision revision
                  ON revision.tenant_id = task.tenant_id
                 AND revision.id = task.state_machine_revision_id
                WHERE task.project_id=?
                  AND task.task_code IN ('T-ASSIGN-SM', 'T-ASSIGN-PM', 'T-TEAM-BUILD')
                  AND task.root_task_id IS NOT NULL
                  AND task.tree_depth IN (0, 1, 2)
                  AND revision.status='PUBLISHED'
                """, Long.class, created.id()));
        assertEquals(1L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM proj_project_task root
                JOIN proj_project_task child
                  ON child.tenant_id=root.tenant_id AND child.project_id=root.project_id
                 AND child.parent_task_id=root.id AND child.root_task_id=root.id AND child.tree_depth=1
                JOIN proj_project_task grandchild
                  ON grandchild.tenant_id=child.tenant_id AND grandchild.project_id=child.project_id
                 AND grandchild.parent_task_id=child.id AND grandchild.root_task_id=root.id
                 AND grandchild.tree_depth=2
                WHERE root.project_id=? AND root.task_code='T-ASSIGN-SM'
                  AND root.parent_task_id IS NULL AND root.root_task_id=root.id AND root.tree_depth=0
                  AND child.task_code='T-ASSIGN-PM' AND grandchild.task_code='T-TEAM-BUILD'
                """, Long.class, created.id()));
        assertEquals(6L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM proj_task_tree_path path
                JOIN proj_project_task ancestor ON ancestor.id=path.ancestor_task_id
                JOIN proj_project_task descendant ON descendant.id=path.descendant_task_id
                WHERE path.project_id=?
                  AND ancestor.task_code IN ('T-ASSIGN-SM', 'T-ASSIGN-PM', 'T-TEAM-BUILD')
                  AND descendant.task_code IN ('T-ASSIGN-SM', 'T-ASSIGN-PM', 'T-TEAM-BUILD')
                """, Long.class, created.id()));
        assertEquals(0L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT task.id
                    FROM proj_project_task task
                    LEFT JOIN proj_project_task_execution_contract contract
                      ON contract.tenant_id=task.tenant_id AND contract.project_task_id=task.id
                     AND contract.current_marker=1 AND contract.deleted=b'0'
                    WHERE task.project_id=?
                    GROUP BY task.id
                    HAVING COUNT(contract.id) <> 1
                ) invalid_contracts
                """, Long.class, created.id()));
    }

    @ParameterizedTest(name = "{0} failure rolls back every fact")
    @EnumSource(FailurePoint.class)
    void everyFailurePointRollsBackAllFacts(FailurePoint point) {
        Map<String, Long> before = factCounts();
        installFailureTrigger(point);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> applicationService.create(newCommand(), newActor()));

        dropFailureTrigger();
        assertTrue(hasCauseMessage(failure, "F-PROJ-001 injected failure"),
                () -> point + "未到达指定MySQL Trigger，实际异常：" + failure);
        assertEquals(before, factCounts());
    }
}

@SpringBootTest(
        classes = ProjectManualCreationMySqlTestSupport.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ProjectManualCreationMySqlTestSupport {

    static final String DATA_PREFIX = "IT-FPROJ001-";
    static final String KEY_PREFIX = "it-fproj001-";
    private static final String FAILURE_TRIGGER = "it_fproj001_failure";

    @Resource
    ProjectManualCreationApplicationService applicationService;
    @Resource
    ProjectTemplateService projectTemplateService;
    @Resource
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = currentEnvironment();
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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void cleanBefore() {
        TenantContextHolder.setTenantId(0L);
        dropFailureTrigger();
        cleanOwnedFacts();
    }

    @AfterEach
    void cleanAfter() {
        try {
            dropFailureTrigger();
            cleanOwnedFacts();
        } finally {
            TenantContextHolder.clear();
        }
    }

    ManualProjectCreateCommand newCommand() {
        return newCommand(KEY_PREFIX + UUID.randomUUID(), sha256(UUID.randomUUID().toString()));
    }

    ManualProjectCreateCommand newCommand(String idempotencyKey, String requestDigest) {
        return newCommand(idempotencyKey, requestDigest, DATA_PREFIX + UUID.randomUUID());
    }

    ManualProjectCreateCommand newCommand(String idempotencyKey, String requestDigest, String projectName) {
        ProjectMasterDO draft = new ProjectMasterDO();
        draft.setProjectName(projectName);
        draft.setCustomerCode("IT-CUSTOMER");
        draft.setCustomerName("集成测试客户");
        draft.setCreationReason("F-PROJ-001真实MySQL原子性验证");
        draft.setSigningMethod("DIRECT_SIGN");
        draft.setProjectCategory("ENGINEERING");
        draft.setImplementationMode("DIRECT_SERVICE");
        draft.setImplementationLocation("集成测试兼容地点");
        TemplateMatchResult match = projectTemplateService.matchPreview(
                draft.getSigningMethod(), draft.getProjectCategory(), draft.getImplementationMode(), null);
        if (match.getOutcome() != TemplateMatchResult.Outcome.MATCHED || match.getMatched() == null) {
            throw new IllegalStateException("真实MySQL集成测试需要V54/V55提供唯一生效模板：outcome="
                    + match.getOutcome() + ", conflicts=" + match.getConflicts()
                    + ", candidates=" + match.getCandidates());
        }
        return new ManualProjectCreateCommand(draft, 1L, 1L, java.util.List.of(),
                match.getMatched().getTemplateRevisionId(), match.getCandidateWatermark(),
                idempotencyKey, requestDigest);
    }

    ProjectManualCreationApplicationService.Actor newActor() {
        return new ProjectManualCreationApplicationService.Actor(
                0L, 9_900_001L, KEY_PREFIX + "correlation-" + UUID.randomUUID());
    }

    Map<String, Long> factCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of(
                "proj_project", "proj_project_stage", "proj_project_task",
                "proj_project_milestone", "proj_project_gate", "proj_project_gate_reference",
                "proj_project_task_execution_contract", "proj_task_tree_path", "acc_project_deliverable",
                "proj_project_template_match_history", "proj_project_tree_version", "proj_project_tree_path",
                "plt_idempotency_record", "plt_operation_audit", "plt_outbox_event")) {
            counts.put(table, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        return counts;
    }

    void installFailureTrigger(FailurePoint point) {
        dropFailureTrigger();
        jdbcTemplate.execute("CREATE TRIGGER " + FAILURE_TRIGGER + " BEFORE " + point.operation
                + " ON " + point.table + " FOR EACH ROW SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT = 'F-PROJ-001 injected failure'");
    }

    void dropFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + FAILURE_TRIGGER);
    }

    void cleanOwnedFacts() {
        List<Long> projectIds = jdbcTemplate.queryForList(
                "SELECT id FROM proj_project WHERE project_name LIKE ?", Long.class, DATA_PREFIX + "%");
        if (!projectIds.isEmpty()) {
            String placeholders = String.join(",", projectIds.stream().map(ignored -> "?").toList());
            Object[] ids = projectIds.toArray();
            // PM-07历史通过专用append-only Mapper封闭写入口；测试也不删除已落历史。
            jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE aggregate_type = 'Project' "
                    + "AND aggregate_key IN (" + placeholders + ")", Arrays.stream(ids)
                    .map(String::valueOf).toArray());
            jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE aggregate_type = 'Project' "
                    + "AND aggregate_key IN (" + placeholders + ")", Arrays.stream(ids)
                    .map(String::valueOf).toArray());
            jdbcTemplate.update("DELETE FROM proj_project_gate_reference WHERE gate_id IN "
                    + "(SELECT id FROM proj_project_gate WHERE project_id IN (" + placeholders + "))", ids);
            jdbcTemplate.update("DELETE FROM proj_project_task_execution_contract WHERE project_task_id IN "
                    + "(SELECT id FROM proj_project_task WHERE project_id IN (" + placeholders + "))", ids);
            jdbcTemplate.update("DELETE FROM proj_task_tree_path WHERE project_id IN (" + placeholders + ")", ids);
            for (String table : List.of("acc_project_deliverable", "proj_project_company_department_relation",
                    "proj_project_member_assignment", "proj_project_gate", "proj_project_milestone",
                    "proj_project_stage")) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE project_id IN (" + placeholders + ")", ids);
            }
            deleteProjectTasksForTest(projectIds, placeholders);
            jdbcTemplate.update("DELETE FROM proj_project_tree_path WHERE root_project_id IN ("
                    + placeholders + ")", ids);
            jdbcTemplate.update("DELETE FROM proj_project_tree_version WHERE root_project_id IN ("
                    + placeholders + ")", ids);
            jdbcTemplate.update("DELETE FROM proj_project WHERE id IN (" + placeholders + ")", ids);
        }
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE correlation_id LIKE ?", KEY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?", KEY_PREFIX + "%");
    }

    private void deleteProjectTasksForTest(List<Long> projectIds, String placeholders) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
            }
            try (var delete = connection.prepareStatement(
                    "DELETE FROM proj_project_task WHERE project_id IN (" + placeholders + ")")) {
                for (int index = 0; index < projectIds.size(); index++) {
                    delete.setLong(index + 1, projectIds.get(index));
                }
                delete.executeUpdate();
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            }
            return null;
        });
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static boolean hasCauseMessage(Throwable failure, String expected) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> currentEnvironment() {
        Map<String, String> values = new LinkedHashMap<>(System.getenv());
        Path dotenv = findRepositoryDotenv();
        if (dotenv == null) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                values.putIfAbsent(trimmed.substring(0, separator).trim(),
                        unquote(trimmed.substring(separator + 1).trim()));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取当前仓库.env", ex);
        }
    }

    private static Path findRepositoryDotenv() {
        for (Path directory = Path.of("").toAbsolutePath().normalize();
                directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve("compose.yaml"))) {
                Path dotenv = directory.resolve(".env");
                return Files.isRegularFile(dotenv) ? dotenv : null;
            }
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("真实MySQL集成测试缺少当前仓库参数：" + key);
        }
        return value;
    }

    enum FailurePoint {
        STAGE("INSERT", "proj_project_stage"),
        TASK("INSERT", "proj_project_task"),
        TASK_TREE_PATH("INSERT", "proj_task_tree_path"),
        MILESTONE("INSERT", "proj_project_milestone"),
        GATE("INSERT", "proj_project_gate"),
        CONTRACT("INSERT", "proj_project_task_execution_contract"),
        ACC_DELIVERABLE("INSERT", "acc_project_deliverable"),
        MATCH_HISTORY("INSERT", "proj_project_template_match_history"),
        TREE_VERSION("INSERT", "proj_project_tree_version"),
        TREE_PATH("INSERT", "proj_project_tree_path"),
        IDEMPOTENCY_SUCCESS("UPDATE", "plt_idempotency_record"),
        AUDIT("INSERT", "plt_operation_audit"),
        OUTBOX("INSERT", "plt_outbox_event");

        private final String operation;
        private final String table;

        FailurePoint(String operation, String table) {
            this.operation = operation;
            this.table = table;
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox"})
    @Import({
            YudaoDataSourceAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class,
            SpringUtil.class,
            ProjectManualCreationApplicationService.class,
            PlatformCommandExecutionApiImpl.class,
            cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter.class,
            PlatformOutboxDeliveryApiImpl.class,
            ProjectManualCreationServiceImpl.class,
            ProjectTemplateServiceImpl.class,
            ProjectAttributeResolutionService.class,
            ProjectTemplateMatchHistoryService.class,
            ProjectAttributeClassificationApplicationService.class,
            ProjectAttributeSourceCorrectionService.class,
            ProjectTemplateMatchHistoryQueryService.class,
            ProjectTreeProjectionService.class,
            ProjectCodeAllocator.class,
            TaskExecutionContractFactory.class,
            ProjectDeliverableInitializationApplicationServiceImpl.class
    })
    static class TestApplication {

        @Bean
        cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi
                projectWorkBindingFactApi() {
            return mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi
                preparationInitializationApi() {
            return mock(cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        AdminUserApi adminUserApi() {
            return mock(AdminUserApi.class);
        }

        @Bean
        CustomerQueryApi customerQueryApi() {
            CustomerQueryApi api = mock(CustomerQueryApi.class);
            when(api.getCustomer(anyLong())).thenAnswer(invocation -> {
                Long customerId = invocation.getArgument(0);
                return new CustomerSummaryDTO(customerId, 0L, "IT-CUSTOMER", "集成测试客户",
                        "集成测试客户", "ENABLED", "PLATFORM", 1L, null);
            });
            return api;
        }

        @Bean
        DeptApi deptApi() {
            DeptApi api = mock(DeptApi.class);
            DeptRespDTO department = new DeptRespDTO();
            department.setId(1L);
            department.setCode("IT-DEPT");
            department.setName("集成测试办事处");
            when(api.getDept(1L)).thenReturn(department);
            return api;
        }

        @Bean
        CompanyApi companyApi() {
            CompanyApi api = mock(CompanyApi.class);
            CompanyRespDTO company = new CompanyRespDTO();
            company.setId(1L);
            company.setCode("IT-COMPANY");
            company.setName("集成测试公司");
            when(api.getCompany(1L)).thenReturn(company);
            return api;
        }

        @Bean
        OrganizationScopeApi organizationScopeApi() {
            OrganizationScopeApi api = mock(OrganizationScopeApi.class);
            when(api.hasScope(anyLong(), anyLong(), anyLong())).thenReturn(true);
            return api;
        }

        @Bean
        ProjectCreationAuthorizationService authorizationService() {
            return mock(ProjectCreationAuthorizationService.class);
        }

        @Bean
        ProjectTreeScopeService projectTreeScopeService() {
            return mock(ProjectTreeScopeService.class);
        }

        @Bean
        ProjectTreeMetrics projectTreeMetrics() {
            return mock(ProjectTreeMetrics.class);
        }

        @Bean
        PermissionCommonApi permissionCommonApi() {
            PermissionCommonApi api = mock(PermissionCommonApi.class);
            when(api.hasAnyPermissions(anyLong(), any())).thenReturn(true);
            return api;
        }

        @Bean
        PermissionApi permissionApi() {
            PermissionApi api = mock(PermissionApi.class);
            when(api.hasAnyPermissions(anyLong(), any())).thenReturn(true);
            return api;
        }

        @Bean
        TrustedProjectServicePrincipalRegistry trustedProjectServicePrincipalRegistry() {
            TrustedProjectServicePrincipalRegistry registry = mock(TrustedProjectServicePrincipalRegistry.class);
            when(registry.resolve("int-crm-sync")).thenReturn(9_900_002L);
            return registry;
        }

        @Bean
        ProjectSiteApplicationService projectSiteApplicationService() {
            ProjectSiteApplicationService service = mock(ProjectSiteApplicationService.class);
            when(service.validateLocationScope(any(), any()))
                    .thenReturn(ProjectSiteApplicationService.LOCATION_UNRESOLVED);
            return service;
        }

        @Bean
        AssetLocationApi assetLocationApi() {
            return mock(AssetLocationApi.class);
        }

        @Bean
        TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
