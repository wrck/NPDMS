package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionBatchCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionBatchDTO;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskCreateItem;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CollectionTaskMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CollectionTaskMySqlTest {

    private static final String KEY_PREFIX = "it-device-ops-";

    @Resource CollectionTaskService service;
    @Resource JdbcTemplate jdbcTemplate;
    private String suffix;

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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.platform");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        suffix = UUID.randomUUID().toString();
        cleanFacts();
    }

    @AfterEach
    void tearDown() {
        try {
            cleanFacts();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void concurrentSameBatchReplaysSinglePersistedBatch() throws Exception {
        CollectionBatchCreateCommand command = command("same", sha256("same"));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<CollectionBatchDTO>> futures = List.of(
                    executor.submit(() -> createAfter(start, command)),
                    executor.submit(() -> createAfter(start, command)));
            start.countDown();

            CollectionBatchDTO first = futures.get(0).get();
            CollectionBatchDTO second = futures.get(1).get();

            assertEquals(first.id(), second.id());
            assertEquals(1L, countBatches());
            assertEquals(1L, countTasks());
        }
    }

    @Test
    void sameIdempotencyKeyWithDifferentDigestConflicts() {
        service.createBatch(command("conflict", sha256("first")));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.createBatch(command("conflict", sha256("second"))));

        assertEquals("IDEMPOTENCY_CONFLICT", failure.getMessage());
        assertEquals(1L, countBatches());
        assertEquals(1L, countTasks());
    }

    private CollectionBatchDTO createAfter(CountDownLatch start, CollectionBatchCreateCommand command)
            throws Exception {
        start.await();
        try {
            TenantContextHolder.setTenantId(0L);
            return service.createBatch(command);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private CollectionBatchCreateCommand command(String keySuffix, String digest) {
        String key = KEY_PREFIX + suffix + "-" + keySuffix;
        CollectionTaskCreateItem task = new CollectionTaskCreateItem(
                "device-" + suffix, "Device", "10.0.0.1", 22, "SSH", "template-1", "v1",
                "a".repeat(64), "SAVED_CREDENTIAL", 9L, 10L, key + "-task",
                "IMP", "ConfigurationCollectionResult", "result-" + suffix);
        return new CollectionBatchCreateCommand(
                0L, 9_900_003L, key, digest, "IMP", "ConfigurationCollectionResult",
                "result-" + suffix, "project-" + suffix, "BUSINESS_CONSUMPTION", List.of(task));
    }

    private long countBatches() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_collection_batch WHERE idempotency_key LIKE ?",
                Long.class, KEY_PREFIX + suffix + "%");
    }

    private long countTasks() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_collection_task WHERE idempotency_key LIKE ?",
                Long.class, KEY_PREFIX + suffix + "%");
    }

    private void cleanFacts() {
        jdbcTemplate.update("DELETE FROM plt_collection_task WHERE idempotency_key LIKE ?", KEY_PREFIX + suffix + "%");
        jdbcTemplate.update("DELETE FROM plt_collection_batch WHERE idempotency_key LIKE ?", KEY_PREFIX + suffix + "%");
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE correlation_id LIKE ?", KEY_PREFIX + suffix + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?", KEY_PREFIX + suffix + "%");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
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

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan("cn.iocoder.yudao.module.pms.platform.dal.mysql")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, CollectionTaskService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
