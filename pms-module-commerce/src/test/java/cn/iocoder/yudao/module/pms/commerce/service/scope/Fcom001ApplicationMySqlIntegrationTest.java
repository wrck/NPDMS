package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.AssetDeviceScopeApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeGuardApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectOfficeFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = Fcom001ApplicationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Fcom001ApplicationMySqlIntegrationTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 992_002_700_001L;
    private static final long PROJECT_ID = 992_002_000_032L;
    private static final long ORDER_LINE_ID = 992_002_300_005L;
    private static final long PROJECT_SCOPE_VERSION = 1L;
    private static final String SOURCE_VERSION = "1";
    private static final String EVIDENCE_PREFIX = "it-fcom001-";
    private static final long INJECTED_OUTBOX_ID = 992_002_799_001L;

    @Resource private CommerceDeliveryScopeCommandService commandService;
    @Resource private JdbcTemplate jdbcTemplate;

    @MockitoBean private ProjectScopeApi projectScopeApi;
    @MockitoBean private ProjectOfficeFactApi projectOfficeFactApi;
    @MockitoBean private AcceptanceStageBindingCoordinator acceptanceBindingCoordinator;
    @MockitoBean private AssetDeviceScopeApi assetDeviceScopeApi;
    @MockitoBean private AcceptanceScopeGuardApi acceptanceScopeGuardApi;
    @MockitoBean private OperationAuditApi operationAuditApi;

    private int projectVersion;

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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.commerce");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        login();
        clean();
        projectVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM proj_project WHERE tenant_id = 0 AND id = ?", Integer.class, PROJECT_ID);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(
                new ProjectScopeResult(PROJECT_ID, PROJECT_SCOPE_VERSION, Set.of(PROJECT_ID), Set.of()));
        when(projectOfficeFactApi.lockAndRevalidate(any())).thenReturn(new ProjectOfficeFact(
                ProjectFactOutcome.FOUND, PROJECT_ID, projectVersion, "FPROJ002-V18-PENDING",
                930_851L, "OFFICE-HZ-DEMO", "杭州示例办事处", 0));
        when(acceptanceBindingCoordinator.lockAndRead(any(), any(), any(), any())).thenReturn(
                new AcceptanceStageBindingCoordinator.StageContext(
                        TENANT_ID, PROJECT_ID, projectVersion, null, false));
    }

    @AfterEach
    void tearDown() {
        try {
            clean();
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    @Test
    void assignPersistsOneCurrentVersionAndReplaysIdempotently() {
        String operationId = EVIDENCE_PREFIX + UUID.randomUUID();
        DeliveryScopeAssignCommand command = command(operationId, new BigDecimal("10"));

        DeliveryScopeCommandResult created = commandService.assign(command);
        DeliveryScopeCommandResult replayed = commandService.assign(command);

        assertFalse(created.replayed());
        assertTrue(replayed.replayed());
        assertEquals(created.deliveryScopeId(), replayed.deliveryScopeId());
        assertEquals(1L, count("com_delivery_scope", "source_evidence", operationId));
        assertEquals(1L, count("com_delivery_scope_detail", "source_record_key", operationId + ":1"));
        assertEquals("F-COM001-PRODUCT-A", jdbcTemplate.queryForObject(
                "SELECT product_code FROM com_delivery_scope_detail WHERE source_record_key = ?",
                String.class, operationId + ":1"));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id = ? AND effective_to IS NULL "
                        + "AND source_evidence = ?", Long.class, ORDER_LINE_ID, operationId));
    }

    @Test
    void outboxFailureRollsBackScopeAndDetail() throws Exception {
        String operationId = EVIDENCE_PREFIX + UUID.randomUUID();
        String eventId = eventId(operationId);
        CountDownLatch ownerEntered = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        when(projectOfficeFactApi.lockAndRevalidate(any())).thenAnswer(invocation -> {
            ownerEntered.countDown();
            if (!releaseOwner.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("OWNER_TEST_LATCH_TIMEOUT");
            }
            return new ProjectOfficeFact(ProjectFactOutcome.FOUND, PROJECT_ID, projectVersion,
                    "FPROJ002-V18-PENDING", 930_851L, "OFFICE-HZ-DEMO", "杭州示例办事处", 0);
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> assignment = executor.submit(() -> {
                TenantContextHolder.setTenantId(TENANT_ID);
                login();
                try {
                    commandService.assign(command(operationId, new BigDecimal("10")));
                } finally {
                    SecurityContextHolder.clearContext();
                    TenantContextHolder.clear();
                }
            });
            assertTrue(ownerEntered.await(10, TimeUnit.SECONDS));
            jdbcTemplate.update("INSERT INTO com_outbox_event "
                            + "(id, event_id, event_type, aggregate_type, aggregate_key, scope_version, payload, "
                            + "status, occurred_at, tenant_id) VALUES (?, ?, 'InjectedFailure', 'DeliveryScope', "
                            + "'injected', 1, JSON_OBJECT('evidence', ?), 'PENDING', NOW(), ?)",
                    INJECTED_OUTBOX_ID, eventId, operationId, TENANT_ID);
            releaseOwner.countDown();
            assertThrows(ExecutionException.class, assignment::get);
        } finally {
            releaseOwner.countDown();
        }

        assertEquals(0L, count("com_delivery_scope", "source_evidence", operationId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_delivery_scope_detail WHERE source_record_key LIKE ?",
                Long.class, operationId + "%"));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_outbox_event WHERE event_id = ?", Long.class,
                eventId));
    }

    @Test
    void concurrentAssignmentHasExactlyOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                String operationId = EVIDENCE_PREFIX + "race-" + index + '-' + UUID.randomUUID();
                futures.add(executor.submit(() -> {
                    TenantContextHolder.setTenantId(TENANT_ID);
                    login();
                    try {
                        start.await();
                        commandService.assign(command(operationId, new BigDecimal("10")));
                        return true;
                    } catch (RuntimeException exception) {
                        assertEquals("DELIVERY_SCOPE_CURRENT_CONFLICT", exception.getMessage());
                        return false;
                    } finally {
                        SecurityContextHolder.clearContext();
                        TenantContextHolder.clear();
                    }
                }));
            }
            start.countDown();
            long winners = 0;
            for (Future<Boolean> future : futures) if (future.get()) winners++;
            assertEquals(1L, winners);
        }
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_delivery_scope WHERE tenant_id = 0 AND project_id = ? "
                        + "AND order_line_id = ? AND effective_to IS NULL AND source_evidence LIKE ?",
                Long.class, PROJECT_ID, ORDER_LINE_ID, EVIDENCE_PREFIX + "race-%"));
    }

    private DeliveryScopeAssignCommand command(String operationId, BigDecimal quantity) {
        return new DeliveryScopeAssignCommand(TENANT_ID, USER_ID, PROJECT_ID, projectVersion,
                PROJECT_SCOPE_VERSION, ORDER_LINE_ID, SOURCE_VERSION, quantity, List.of(),
                "真实MySQL事务验证", operationId);
    }

    private long count(String table, String column, String value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, value);
    }

    private String eventId(String operationId) {
        return UUID.nameUUIDFromBytes((TENANT_ID + ":DIRECT_SCOPE:ASSIGN:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private void clean() {
        jdbcTemplate.update("DELETE FROM com_outbox_event WHERE payload LIKE ?",
                "%" + EVIDENCE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM com_delivery_scope_detail WHERE source_record_key LIKE ?",
                EVIDENCE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM com_delivery_scope WHERE source_evidence LIKE ?",
                EVIDENCE_PREFIX + "%");
    }

    private static void login() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(USER_ID).setTenantId(TENANT_ID).setUserType(2),
                new MockHttpServletRequest());
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.commerce.dal.mysql")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            CommerceDeliveryScopeCommandService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
