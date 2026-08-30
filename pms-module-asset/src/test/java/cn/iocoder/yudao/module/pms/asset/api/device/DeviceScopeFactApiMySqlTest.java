package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationResult;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = DeviceScopeFactApiMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeviceScopeFactApiMySqlTest {

    @Resource private DeviceScopeFactApi api;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private DataSource dataSource;
    @Resource private TransactionTemplate transactionTemplate;

    private long idBase;
    private String prefix;

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
        idBase = 987_000_000_000L
                + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 10L;
        prefix = "IT-AST-SCOPE-" + idBase + "-";
        insert(1L, idBase + 1, prefix + "ACTIVE", "ACTIVE", 10L, 7L);
        insert(1L, idBase + 2, prefix + "RETIRED", "RETIRED", 10L, 1L);
        insert(1L, idBase + 3, prefix + "OTHER-PROJECT", "ACTIVE", 99L, 2L);
        insert(2L, idBase + 4, prefix + "OTHER-TENANT", "ACTIVE", 10L, 3L);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM ast_device WHERE id BETWEEN ? AND ?", idBase + 1, idBase + 4);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void resolvesOwnerFactsAndFailsClosedForStatusProjectAndTenant() {
        var resolved = api.resolveBySerials(new DeviceScopeResolveQuery(
                1L, 10L, List.of((prefix + "active").toLowerCase())));
        assertEquals("RESOLVED", resolved.decision().name());
        assertEquals(idBase + 1, resolved.fact().devices().getFirst().deviceId());

        var invalid = api.resolveBySerials(new DeviceScopeResolveQuery(1L, 10L,
                List.of(prefix + "RETIRED", prefix + "OTHER-PROJECT", prefix + "OTHER-TENANT")));
        assertEquals(List.of("STATUS_INELIGIBLE", "PROJECT_MISMATCH", "NOT_FOUND"),
                invalid.invalidItems().stream().map(item -> item.reason().name()).toList());
    }

    @Test
    void lockRevalidationReturnsStaleAndRejectsChangedSerialIdentity() {
        DeviceScopeRevalidationQuery expected = expected(prefix + "ACTIVE", 7L);
        jdbcTemplate.update("UPDATE ast_device SET project_assignment_version=8 WHERE tenant_id=1 AND id=?",
                idBase + 1);
        assertEquals(DeviceScopeRevalidationResult.Decision.STALE,
                api.lockAndRevalidate(expected).decision());

        jdbcTemplate.update("UPDATE ast_device SET sn=? WHERE tenant_id=1 AND id=?",
                prefix + "RENAMED", idBase + 1);
        DeviceScopeFactException failure = assertThrows(DeviceScopeFactException.class,
                () -> api.lockAndRevalidate(expected));
        assertEquals(DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED, failure.getCode());
    }

    @Test
    void lockRevalidationHoldsTheDeviceRowUntilOuterTransactionCompletes() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> owner = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                TenantContextHolder.setTenantId(1L);
                try {
                    api.lockAndRevalidate(expected(prefix + "ACTIVE", 7L));
                    locked.countDown();
                    await(release);
                } finally {
                    TenantContextHolder.clear();
                }
            }));
            locked.await();
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION innodb_lock_wait_timeout=1");
                SQLException timeout = assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "UPDATE ast_device SET project_assignment_version=9 WHERE tenant_id=1 AND id=" + (idBase + 1)));
                assertEquals(1205, timeout.getErrorCode());
            } finally {
                release.countDown();
            }
            owner.get();
        }
        assertEquals(7L, jdbcTemplate.queryForObject(
                "SELECT project_assignment_version FROM ast_device WHERE tenant_id=1 AND id=?",
                Long.class, idBase + 1));
    }

    private DeviceScopeRevalidationQuery expected(String serialNumber, long version) {
        return new DeviceScopeRevalidationQuery(1L, 10L,
                List.of(new DeviceScopeRevalidationQuery.ExpectedDevice(idBase + 1, serialNumber, version)),
                new DeviceScopeRevalidationQuery.ExpectedScopeWatermark(List.of(
                        new DeviceScopeRevalidationQuery.ExpectedWatermarkEntry(idBase + 1, version))));
    }

    private void insert(long tenantId, long id, String serialNumber, String status,
                        Long projectId, long assignmentVersion) {
        jdbcTemplate.update("INSERT INTO ast_device "
                        + "(id,sn,name,project_id,project_assignment_version,customer_assignment_version,"
                        + "status,source_system,source_key,sync_status,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,?,?,0,?,'PMS',?,'FRESH',0,'mysql-it','mysql-it',b'0',?)",
                id, serialNumber, serialNumber, projectId, assignmentVersion,
                status, "it-scope-" + id, tenantId);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("lock test interrupted", exception);
        }
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
        } catch (IOException exception) {
            throw new IllegalStateException("读取真实MySQL集成测试环境失败", exception);
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
    @MapperScan("cn.iocoder.yudao.module.pms.asset.dal.mysql.device")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, DeviceScopeFactApiImpl.class,
            DeviceScopeFactTransactionExecutor.class})
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
