package cn.iocoder.yudao.module.pms.asset.service.assembly;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.service.assembly.command.ApplyDeviceAssemblyCommand;
import cn.iocoder.yudao.module.pms.asset.service.location.DeviceLocationEffectiveService;
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
@SpringBootTest(classes = DeviceAssemblyLocationConcurrencyMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeviceAssemblyLocationConcurrencyMySqlTest {

    @Resource private DeviceAssemblyService assemblyService;
    @Resource private DeviceLocationEffectiveService locationService;
    @Resource private JdbcTemplate jdbcTemplate;

    private long idBase;
    private String snA;
    private String snB;
    private String snLocation;
    private String sourcePrefix;

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
        idBase = 982_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 10L;
        snA = "IT-AST-ASM-A-" + idBase;
        snB = "IT-AST-ASM-B-" + idBase;
        snLocation = "IT-AST-LOC-" + idBase;
        sourcePrefix = "it-task10-" + idBase;
        insertDevice(idBase + 1, snA);
        insertDevice(idBase + 2, snB);
        insertDevice(idBase + 3, snLocation);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM ast_device_assembly WHERE tenant_id=1 AND source_key LIKE ?", sourcePrefix + "%");
            jdbcTemplate.update("DELETE FROM ast_device_location WHERE tenant_id=1 AND device_sn=?", snLocation);
            jdbcTemplate.update("DELETE FROM ast_device WHERE tenant_id=1 AND id BETWEEN ? AND ?", idBase + 1, idBase + 3);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void concurrentReverseAssembliesMustNotCreateCycle() throws Exception {
        LocalDateTime effectiveAt = LocalDateTime.of(2026, 8, 27, 16, 30);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> assembleAfter(ready, start, snA, snB, "SLOT-A", "-a", effectiveAt)),
                    executor.submit(() -> assembleAfter(ready, start, snB, snA, "SLOT-B", "-b", effectiveAt)));
            ready.await();
            start.countDown();
            List<Object> outcomes = collect(futures);

            assertEquals(1, outcomes.stream().filter(Boolean.TRUE::equals).count(), outcomes.toString());
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ast_device_assembly WHERE tenant_id=1 AND source_key LIKE ? "
                            + "AND effective_to IS NULL AND deleted=b'0'",
                    Long.class, sourcePrefix + "%"));
        }
    }

    @Test
    void concurrentLocationsFromSameDeviceVersionHaveOneSuccessAndNoProjectionDrift() throws Exception {
        LocalDateTime effectiveAt = LocalDateTime.of(2026, 8, 27, 16, 45);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> locateAfter(ready, start, 701L, "机柜A", effectiveAt)),
                    executor.submit(() -> locateAfter(ready, start, 702L, "机柜B", effectiveAt)));
            ready.await();
            start.countDown();
            List<Object> outcomes = collect(futures);

            assertEquals(2, outcomes.stream().filter(Boolean.TRUE::equals).count(), outcomes.toString());
            assertEquals(2L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ast_device_location WHERE tenant_id=1 AND device_sn=? AND deleted=b'0'",
                    Long.class, snLocation));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ast_device_location WHERE tenant_id=1 AND device_sn=? "
                            + "AND effective_to IS NULL AND deleted=b'0'",
                    Long.class, snLocation));
            assertEquals(jdbcTemplate.queryForObject(
                            "SELECT id FROM ast_device_location WHERE tenant_id=1 AND device_sn=? "
                                    + "AND effective_to IS NULL AND deleted=b'0'",
                            Long.class, snLocation),
                    jdbcTemplate.queryForObject(
                            "SELECT location_record_id FROM ast_device WHERE tenant_id=1 AND id=?",
                            Long.class, idBase + 3));
        }
    }

    private Object assembleAfter(CountDownLatch ready, CountDownLatch start,
                                 String parentSn, String childSn, String position,
                                 String suffix, LocalDateTime effectiveAt) {
        TenantContextHolder.setTenantId(1L);
        try {
            ready.countDown();
            start.await();
            assemblyService.apply(new ApplyDeviceAssemblyCommand(
                    1L, parentSn, childSn, position, "PHYSICAL", effectiveAt,
                    null, "AST", sourcePrefix + suffix, "1"));
            return true;
        } catch (Throwable failure) {
            return failure;
        } finally {
            TenantContextHolder.clear();
        }
    }

    private Object locateAfter(CountDownLatch ready, CountDownLatch start,
                               long installationId, String snapshot, LocalDateTime effectiveAt) {
        TenantContextHolder.setTenantId(1L);
        try {
            ready.countDown();
            start.await();
            return locationService.effect(new EquipmentLocationEffectiveCommand(
                    idBase + 3, installationId, null, null, snapshot,
                    "UNRESOLVED", snapshot, effectiveAt));
        } catch (Throwable failure) {
            return failure;
        } finally {
            TenantContextHolder.clear();
        }
    }

    private List<Object> collect(List<Future<Object>> futures) throws Exception {
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            outcomes.add(future.get());
        }
        return outcomes;
    }

    private void insertDevice(long id, String sn) {
        jdbcTemplate.update("INSERT INTO ast_device "
                        + "(id,sn,name,project_assignment_version,customer_assignment_version,status,source_system,source_key,"
                        + "sync_status,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,0,0,'ACTIVE','PMS',?,'FRESH',0,'mysql-it','mysql-it',b'0',1)",
                id, sn, sn, sn);
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
    @MapperScan({"cn.iocoder.yudao.module.pms.asset.dal.mysql.device",
            "cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly",
            "cn.iocoder.yudao.module.pms.asset.dal.mysql.location"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            DeviceAssemblyService.class, DeviceLocationEffectiveService.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
