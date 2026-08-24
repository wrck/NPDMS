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
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationServiceImpl;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
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
                "proj_project_task_execution_contract", "acc_project_deliverable",
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
            for (String table : List.of("acc_project_deliverable", "proj_project_company_department_relation",
                    "proj_project_member_assignment", "proj_project_gate", "proj_project_milestone",
                    "proj_project_task", "proj_project_stage")) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE project_id IN (" + placeholders + ")", ids);
            }
            jdbcTemplate.update("DELETE FROM proj_project WHERE id IN (" + placeholders + ")", ids);
        }
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE correlation_id LIKE ?", KEY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?", KEY_PREFIX + "%");
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
        MILESTONE("INSERT", "proj_project_milestone"),
        GATE("INSERT", "proj_project_gate"),
        CONTRACT("INSERT", "proj_project_task_execution_contract"),
        ACC_DELIVERABLE("INSERT", "acc_project_deliverable"),
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
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
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
            ProjectManualCreationServiceImpl.class,
            ProjectTemplateServiceImpl.class,
            ProjectCodeAllocator.class,
            TaskExecutionContractFactory.class,
            ProjectDeliverableInitializationApplicationServiceImpl.class
    })
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        AdminUserApi adminUserApi() {
            return mock(AdminUserApi.class);
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
        PermissionCommonApi permissionCommonApi() {
            PermissionCommonApi api = mock(PermissionCommonApi.class);
            when(api.hasAnyPermissions(anyLong(), any())).thenReturn(true);
            return api;
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
