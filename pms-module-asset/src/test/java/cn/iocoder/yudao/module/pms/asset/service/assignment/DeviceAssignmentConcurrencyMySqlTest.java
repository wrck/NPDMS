package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceProjectCommand;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceProjectAssignmentResult;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.RebuildDeviceAncestorProjectionCommand;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = DeviceAssignmentConcurrencyMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeviceAssignmentConcurrencyMySqlTest {

    private static final String KEY_PREFIX = "it-ast-assign-";
    private static final long ACTOR_ID = 9_900_001L;

    @Resource private DeviceProjectAssignmentService assignmentService;
    @Resource private DeviceAncestorProjectionService projectionService;
    @Resource private JdbcTemplate jdbcTemplate;

    private long deviceId;
    private String deviceSn;
    private String keyPrefix;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> values = environment();
        String port = values.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        String database = values.getOrDefault("NPDMS_DB_NAME", "npdms");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(values, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(values, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.asset");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        deviceId = 980_000_000_000L
                + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        deviceSn = "IT-AST-ASSIGN-" + deviceId;
        keyPrefix = KEY_PREFIX + deviceId + "-";
        jdbcTemplate.update("INSERT INTO ast_device "
                        + "(id,sn,name,project_assignment_version,customer_assignment_version,"
                        + "status,source_system,source_key,sync_status,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,0,0,'ACTIVE','PMS',?,'FRESH',0,'mysql-it','mysql-it',b'0',1)",
                deviceId, deviceSn, deviceSn, deviceSn);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM ast_device_assignment_reconciliation "
                    + "WHERE tenant_id=1 AND device_sn=?", deviceSn);
            jdbcTemplate.update("DELETE FROM ast_device_project_ancestor "
                    + "WHERE tenant_id=1 AND device_sn=?", deviceSn);
            jdbcTemplate.update("DELETE FROM ast_device_ancestor_projection_operation "
                    + "WHERE tenant_id=1 AND device_sn=?", deviceSn);
            jdbcTemplate.update("DELETE FROM ast_device_project_relationship "
                    + "WHERE tenant_id=1 AND device_sn=?", deviceSn);
            jdbcTemplate.update("DELETE FROM plt_outbox_event "
                    + "WHERE tenant_id=1 AND aggregate_type='Device' AND aggregate_key=?", String.valueOf(deviceId));
            jdbcTemplate.update("DELETE FROM plt_operation_audit "
                    + "WHERE tenant_id=1 AND aggregate_type='Device' AND aggregate_key=?", String.valueOf(deviceId));
            jdbcTemplate.update("DELETE FROM plt_idempotency_record "
                    + "WHERE tenant_id=1 AND scope_code=? AND idempotency_key LIKE ?",
                    DeviceProjectAssignmentService.ASSIGN_SCOPE, keyPrefix + "%");
            jdbcTemplate.update("DELETE FROM ast_device WHERE tenant_id=1 AND id=?", deviceId);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void concurrentProjectAssignmentsFromSameVersionHaveOneCompleteSuccessFact() throws Exception {
        LocalDateTime effectiveAt = LocalDateTime.of(2026, 8, 27, 12, 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> assignAfter(ready, start, 201L, "a", effectiveAt)),
                    executor.submit(() -> assignAfter(ready, start, 202L, "b", effectiveAt)));
            ready.await();
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }

            assertEquals(1, outcomes.stream()
                    .filter(DeviceProjectAssignmentResult.class::isInstance).count());
            Throwable failure = (Throwable) outcomes.stream()
                    .filter(Throwable.class::isInstance).findFirst().orElseThrow();
            assertTrue(failureChain(failure).contains("VERSION_CONFLICT"), failureChain(failure));
            DeviceProjectAssignmentResult success = (DeviceProjectAssignmentResult) outcomes.stream()
                    .filter(DeviceProjectAssignmentResult.class::isInstance).findFirst().orElseThrow();

            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT project_assignment_version FROM ast_device WHERE tenant_id=1 AND id=?",
                    Long.class, deviceId));
            assertEquals(success.projectId(), jdbcTemplate.queryForObject(
                    "SELECT project_id FROM ast_device WHERE tenant_id=1 AND id=?",
                    Long.class, deviceId));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ast_device_project_relationship "
                            + "WHERE tenant_id=1 AND device_sn=? AND relationship_type='DIRECT' "
                            + "AND effective_to IS NULL AND deleted=b'0'",
                    Long.class, deviceSn));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plt_operation_audit "
                            + "WHERE tenant_id=1 AND operation_code='DEVICE_ASSIGN_PROJECT' "
                            + "AND aggregate_type='Device' AND aggregate_key=?",
                    Long.class, String.valueOf(deviceId)));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plt_outbox_event "
                            + "WHERE tenant_id=1 AND event_type='DeviceAssigned' "
                            + "AND aggregate_type='Device' AND aggregate_key=?",
                    Long.class, String.valueOf(deviceId)));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plt_idempotency_record "
                            + "WHERE tenant_id=1 AND scope_code=? AND idempotency_key LIKE ? AND status='COMPLETED'",
                    Long.class, DeviceProjectAssignmentService.ASSIGN_SCOPE, keyPrefix + "%"));
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plt_idempotency_record "
                            + "WHERE tenant_id=1 AND scope_code=? AND idempotency_key LIKE ? AND status='IN_PROGRESS'",
                    Long.class, DeviceProjectAssignmentService.ASSIGN_SCOPE, keyPrefix + "%"));
        }
    }

    @Test
    void repeatedProjectionEventUsesPersistedEventId() {
        RebuildDeviceAncestorProjectionCommand command = projectionCommand();

        assertTrue(projectionService.rebuild(command));
        assertTrue(!projectionService.rebuild(command));
        assertProjectionAppliedOnce();
    }

    @Test
    void concurrentRepeatedProjectionEventHasOneAppliedResult() throws Exception {
        RebuildDeviceAncestorProjectionCommand command = projectionCommand();
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> rebuildAfter(ready, start, command)));
            }
            ready.await();
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }

            assertEquals(1, outcomes.stream().filter(Boolean.TRUE::equals).count());
            assertEquals(workers - 1L,
                    outcomes.stream().filter(Boolean.FALSE::equals).count());
            assertProjectionAppliedOnce();
        }
    }

    @Test
    void concurrentOutOfOrderProjectionKeepsNewestAssignmentVersion() throws Exception {
        RebuildDeviceAncestorProjectionCommand oldCommand =
                new RebuildDeviceAncestorProjectionCommand(
                        1L, deviceSn, 201L, List.of(100L),
                        3L, 1L, keyPrefix + "event-old", keyPrefix + "operation-old");
        RebuildDeviceAncestorProjectionCommand newCommand =
                new RebuildDeviceAncestorProjectionCommand(
                        1L, deviceSn, 202L, List.of(100L, 101L),
                        3L, 2L, keyPrefix + "event-new", keyPrefix + "operation-new");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> rebuildAfter(ready, start, oldCommand)),
                    executor.submit(() -> rebuildAfter(ready, start, newCommand)));
            ready.await();
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }

            assertEquals(0, outcomes.stream().filter(Throwable.class::isInstance).count());
            assertEquals(2L, jdbcTemplate.queryForObject(
                    "SELECT MAX(assignment_version) "
                            + "FROM ast_device_ancestor_projection_operation "
                            + "WHERE tenant_id=1 AND device_sn=?",
                    Long.class, deviceSn));
            assertEquals(2L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ast_device_project_ancestor "
                            + "WHERE tenant_id=1 AND device_sn=? "
                            + "AND project_id=202 AND assignment_version=2",
                    Long.class, deviceSn));
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ast_device_project_ancestor "
                            + "WHERE tenant_id=1 AND device_sn=? "
                            + "AND assignment_version<2",
                    Long.class, deviceSn));
        }
    }

    private RebuildDeviceAncestorProjectionCommand projectionCommand() {
        return new RebuildDeviceAncestorProjectionCommand(
                1L, deviceSn, 202L, List.of(100L),
                3L, 1L, keyPrefix + "event", keyPrefix + "operation");
    }

    private void assertProjectionAppliedOnce() {
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ast_device_ancestor_projection_operation "
                        + "WHERE tenant_id=1 AND event_id=?",
                Long.class, keyPrefix + "event"));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ast_device_project_ancestor "
                        + "WHERE tenant_id=1 AND device_sn=?",
                Long.class, deviceSn));
    }

    private Object rebuildAfter(CountDownLatch ready, CountDownLatch start,
                                RebuildDeviceAncestorProjectionCommand command) {
        TenantContextHolder.setTenantId(1L);
        try {
            ready.countDown();
            start.await();
            return projectionService.rebuild(command);
        } catch (Throwable failure) {
            return failure;
        } finally {
            TenantContextHolder.clear();
        }
    }

    private Object assignAfter(CountDownLatch ready, CountDownLatch start,
                               long projectId, String suffix, LocalDateTime effectiveAt) {
        TenantContextHolder.setTenantId(1L);
        try {
            ready.countDown();
            start.await();
            return assignmentService.assign(new AssignDeviceProjectCommand(
                    1L, deviceId, projectId, 0L, "真实MySQL并发归属验证",
                    keyPrefix + suffix, suffix.repeat(64), ACTOR_ID,
                    keyPrefix + "correlation-" + suffix, effectiveAt));
        } catch (Throwable failure) {
            return failure;
        } finally {
            TenantContextHolder.clear();
        }
    }

    private static String failureChain(Throwable failure) {
        StringBuilder summary = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (!summary.isEmpty()) {
                summary.append(" <- ");
            }
            summary.append(current.getClass().getSimpleName())
                    .append(": ").append(current.getMessage());
        }
        return summary.toString();
    }

    private static Map<String, String> environment() {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path dotenv = findDotenv();
        if (dotenv == null) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#") || !value.contains("=")) {
                    continue;
                }
                int separator = value.indexOf('=');
                values.putIfAbsent(value.substring(0, separator).trim(),
                        unquote(value.substring(separator + 1).trim()));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("读取真实MySQL集成测试环境失败", ex);
        }
    }

    private static Path findDotenv() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("compose.yaml"))) {
                return Files.isRegularFile(path.resolve(".env")) ? path.resolve(".env") : null;
            }
        }
        return null;
    }

    private static String unquote(String value) {
        return value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("真实MySQL集成测试缺少当前仓库参数：" + key);
        }
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            DeviceProjectAssignmentService.class, DeviceAncestorProjectionService.class,
            PlatformCommandExecutionApiImpl.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ProjectDeviceAssignmentGuardApi projectDeviceAssignmentGuardApi() {
            return query -> new ProjectDeviceAssignmentGuardResult(
                    query.projectId(), query.tenantId(), null,
                    query.projectId(), 1L, true, null);
        }
    }
}
