package cn.iocoder.yudao.module.pms.asset.api.customer;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = AssetCustomerDeviceSummaryMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AssetCustomerDeviceSummaryMySqlTest {

    @Resource private AssetCustomerDeviceSummaryApiImpl summaryApi;
    @Resource private JdbcTemplate jdbcTemplate;

    private long idBase;
    private long customerId;

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
        idBase = 983_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 10L;
        customerId = idBase + 9;
        insertDevice(idBase + 1, "CURRENT", customerId);
        insertDevice(idBase + 2, "LEASE", null);
        insertDevice(idBase + 3, "CO_MANAGED", null);
        insertDevice(idBase + 4, "EXPIRED", null);
        insertDevice(idBase + 5, "FUTURE", null);
        insertDevice(idBase + 6, "DELETED_RELATION", null);
        insertDevice(idBase + 7, "DUPLICATE", customerId);
        insertRelationship(idBase + 101, idBase + 2, "LEASE", LocalDateTime.now().minusDays(2), null, false);
        insertRelationship(idBase + 102, idBase + 3, "CO_MANAGED", LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(2), false);
        insertRelationship(idBase + 103, idBase + 4, "HISTORY", LocalDateTime.now().minusDays(4), LocalDateTime.now().minusDays(1), false);
        insertRelationship(idBase + 104, idBase + 5, "LEASE", LocalDateTime.now().plusDays(1), null, false);
        insertRelationship(idBase + 105, idBase + 6, "CO_MANAGED", LocalDateTime.now().minusDays(2), null, true);
        insertRelationship(idBase + 106, idBase + 7, "LEASE", LocalDateTime.now().minusDays(2), null, false);
        insertRelationship(idBase + 107, idBase + 7, "CO_MANAGED", LocalDateTime.now().minusDays(1), null, false);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM ast_device_customer_relationship WHERE tenant_id=1 AND source_key LIKE ?",
                    "it-task16-" + idBase + "%");
            jdbcTemplate.update("DELETE FROM ast_device WHERE tenant_id=1 AND id BETWEEN ? AND ?", idBase + 1, idBase + 7);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void mergesCurrentProjectionAndEffectiveRelationshipsWithoutDuplicates() {
        CustomerDeviceSummarySlice result = summaryApi.query(
                new CustomerDeviceSummaryQuery(1L, customerId, 7L, 1, 20));

        assertTrue(result.available());
        assertEquals(4L, result.total());
        assertEquals(4, result.items().size());
        assertEquals(1L, result.items().stream().filter(item -> item.deviceCode().endsWith("-DUPLICATE")).count());
        assertTrue(result.items().stream().anyMatch(item -> item.deviceCode().endsWith("-CURRENT")));
        assertTrue(result.items().stream().anyMatch(item -> item.deviceCode().endsWith("-LEASE")));
        assertTrue(result.items().stream().anyMatch(item -> item.deviceCode().endsWith("-CO_MANAGED")));
    }

    @Test
    void returnsAvailableEmptyPageWhenCustomerHasNoCurrentOrEffectiveRelationship() {
        CustomerDeviceSummarySlice result = summaryApi.query(
                new CustomerDeviceSummaryQuery(1L, customerId + 1, 7L, 1, 20));

        assertTrue(result.available());
        assertEquals(0L, result.total());
        assertTrue(result.items().isEmpty());
    }

    private void insertDevice(long id, String suffix, Long currentCustomerId) {
        jdbcTemplate.update("INSERT INTO ast_device "
                        + "(id,sn,name,project_id,project_assignment_version,customer_id,customer_assignment_version,"
                        + "status,source_system,source_key,sync_status,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,42,0,?,0,'ACTIVE','PMS',?,'FRESH',0,'mysql-it','mysql-it',b'0',1)",
                id, "IT-TASK16-" + idBase + "-" + suffix, "Task16 " + suffix, currentCustomerId,
                "it-task16-device-" + id);
    }

    private void insertRelationship(long id, long deviceId, String type, LocalDateTime effectiveFrom,
                                    LocalDateTime effectiveTo, boolean deleted) {
        String deviceSn = jdbcTemplate.queryForObject(
                "SELECT sn FROM ast_device WHERE tenant_id=1 AND id=?", String.class, deviceId);
        jdbcTemplate.update("INSERT INTO ast_device_customer_relationship "
                        + "(id,device_sn,customer_id,relationship_type,effective_from,effective_to,assignment_version,"
                        + "reason,operation_id,source_system,source_key,source_version,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,1,'Task16','it-task16-op','PMS',?,'1',0,'mysql-it','mysql-it',?,1)",
                id, deviceSn, customerId, type, effectiveFrom, effectiveTo,
                "it-task16-" + idBase + "-" + id, deleted);
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
    @MapperScan("cn.iocoder.yudao.module.pms.asset.dal.mysql.device")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            AssetCustomerDeviceSummaryApiImpl.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        DeviceAccessScopeService deviceAccessScopeService() {
            DeviceAccessScopeService service = mock(DeviceAccessScopeService.class);
            when(service.visibleProjectIds(1L, 7L)).thenReturn(Set.of(42L));
            return service;
        }
    }
}
