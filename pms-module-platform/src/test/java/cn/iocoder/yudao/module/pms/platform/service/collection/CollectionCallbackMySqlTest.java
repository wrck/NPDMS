package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackResultDTO;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionConsumptionCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionConsumptionResultDTO;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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
@SpringBootTest(classes = CollectionCallbackMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CollectionCallbackMySqlTest {

    private static final Long TENANT_ID = 0L;
    private static final String KEY_PREFIX = "it-collection-callback-";

    @Resource CollectionCallbackService service;
    @Resource JdbcTemplate jdbcTemplate;
    private String suffix;
    private Long batchId;
    private String platformTaskId;
    private String externalTaskId;

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
        TenantContextHolder.setTenantId(TENANT_ID);
        suffix = UUID.randomUUID().toString();
        batchId = positiveLong(suffix + "-batch");
        platformTaskId = KEY_PREFIX + suffix;
        externalTaskId = "external-" + suffix;
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
    void persistsCallbackTaskBatchAndOutboxAtomically() {
        insertTask("BUSINESS_CONSUMPTION");

        CollectionCallbackResultDTO result = service.handleCallback(callback("callback-1", 1L));

        assertEquals("RESULT_AVAILABLE", result.status());
        assertEquals(1L, count("plt_collection_callback_record", "platform_task_id"));
        assertEquals("RESULT_AVAILABLE", taskValue("status", String.class));
        assertEquals(1L, taskValue("last_callback_sequence", Long.class));
        assertEquals(1, batchValue("success_count", Integer.class));
        assertEquals(1L, countOutbox("CollectionResultAvailable"));
    }

    @Test
    void sequenceGapMarksReconciliationWithoutCreatingFacts() {
        insertTask("BUSINESS_CONSUMPTION");

        CollectionCallbackResultDTO result = service.handleCallback(callback("callback-2", 2L));

        assertEquals("RECONCILING", result.technicalStage());
        assertEquals("RECONCILING", taskValue("technical_stage", String.class));
        assertEquals(0L, count("plt_collection_callback_record", "platform_task_id"));
        assertEquals(0, batchValue("success_count", Integer.class));
        assertEquals(0L, countOutbox("CollectionResultAvailable"));
    }

    @Test
    void callbackTerminalPublishesResultAndCompleted() {
        insertTask("CALLBACK_TERMINAL");

        CollectionCallbackResultDTO result = service.handleCallback(callback("callback-1", 1L));

        assertEquals("COMPLETED", result.status());
        assertEquals(1L, countOutbox("CollectionResultAvailable"));
        assertEquals(1L, countOutbox("CollectionCompleted"));
    }

    @Test
    void concurrentCallbackIdProducesSingleProjectionAndOutbox() throws Exception {
        insertTask("BUSINESS_CONSUMPTION");
        CollectionCallbackCommand command = callback("callback-concurrent", 1L);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<CollectionCallbackResultDTO>> futures = List.of(
                    executor.submit(() -> callbackAfter(start, command)),
                    executor.submit(() -> callbackAfter(start, command)));
            start.countDown();
            List<CollectionCallbackResultDTO> results = List.of(
                    futures.get(0).get(), futures.get(1).get());

            assertTrue(results.stream().anyMatch(CollectionCallbackResultDTO::duplicate));
            assertEquals(1L, count("plt_collection_callback_record", "platform_task_id"));
            assertEquals(1, batchValue("success_count", Integer.class));
            assertEquals(1L, countOutbox("CollectionResultAvailable"));
        }
    }

    @Test
    void concurrentConsumptionProducesSingleFactAndCompletionEvents() throws Exception {
        insertTask("BUSINESS_CONSUMPTION");
        service.handleCallback(callback("callback-1", 1L));
        CollectionConsumptionCommand command = consumption();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<CollectionConsumptionResultDTO>> futures = List.of(
                    executor.submit(() -> consumptionAfter(start, command)),
                    executor.submit(() -> consumptionAfter(start, command)));
            start.countDown();
            List<CollectionConsumptionResultDTO> results = List.of(
                    futures.get(0).get(), futures.get(1).get());

            assertTrue(results.stream().anyMatch(CollectionConsumptionResultDTO::duplicate));
            assertEquals(1L, count("plt_collection_result_consumption", "platform_task_id"));
            assertEquals("COMPLETED", taskValue("status", String.class));
            assertEquals(1L, countOutbox("CollectionResultConsumed"));
            assertEquals(1L, countOutbox("CollectionCompleted"));
        }
    }

    private CollectionCallbackResultDTO callbackAfter(CountDownLatch start,
                                                       CollectionCallbackCommand command) throws Exception {
        start.await();
        try {
            TenantContextHolder.setTenantId(TENANT_ID);
            return service.handleCallback(command);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private CollectionConsumptionResultDTO consumptionAfter(CountDownLatch start,
                                                              CollectionConsumptionCommand command)
            throws Exception {
        start.await();
        try {
            TenantContextHolder.setTenantId(TENANT_ID);
            return service.confirmConsumption(command);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void insertTask(String completionMode) {
        jdbcTemplate.update("INSERT INTO plt_collection_batch "
                        + "(id, tenant_id, batch_no, source_context, source_object_type, source_object_id, "
                        + "idempotency_key, status, task_count, success_count, failure_count) "
                        + "VALUES (?, ?, ?, 'IMP', 'IMPLEMENTATION_TASK', ?, ?, 'EXECUTING', 1, 0, 0)",
                batchId, TENANT_ID, "batch-" + suffix, "imp-" + suffix, platformTaskId + "-batch-key");
        jdbcTemplate.update("INSERT INTO plt_collection_task "
                        + "(id, tenant_id, batch_id, platform_task_id, source_context, source_object_type, "
                        + "source_object_id, device_id, device_name, host, port, protocol, template_id, "
                        + "template_version, template_hash, credential_mode, idempotency_key, completion_mode, "
                        + "status, technical_stage, external_task_id, external_status, consumer_context, "
                        + "consumer_object_type, consumer_object_id) "
                        + "VALUES (?, ?, ?, ?, 'IMP', 'IMPLEMENTATION_TASK', ?, ?, ?, '10.0.0.1', 22, 'SSH', "
                        + "'template-1', 'v1', ?, 'SAVED_CREDENTIAL', ?, ?, 'DISPATCHED', 'ACCEPTED', ?, "
                        + "'ACCEPTED', 'IMP', 'IMPLEMENTATION_TASK', ?)",
                positiveLong(suffix + "-task"), TENANT_ID, batchId, platformTaskId,
                "imp-" + suffix, "device-" + suffix, "device-" + suffix,
                "a".repeat(64), platformTaskId + "-task-key", completionMode, externalTaskId,
                "imp-" + suffix);
    }

    private CollectionCallbackCommand callback(String callbackIdSuffix, Long sequence) {
        return new CollectionCallbackCommand(positiveLong(suffix + "-receipt"),
                platformTaskId + "-" + callbackIdSuffix, sequence, platformTaskId, externalTaskId,
                "SUCCEEDED", 1L, positiveLong(suffix + "-file"), null, null,
                LocalDateTime.of(2026, 8, 28, 15, 0),
                LocalDateTime.of(2026, 8, 28, 15, 1), "trace-" + suffix);
    }

    private CollectionConsumptionCommand consumption() {
        return new CollectionConsumptionCommand(platformTaskId, "IMP", "IMPLEMENTATION_TASK",
                "imp-" + suffix, 1L, "trace-" + suffix);
    }

    private <T> T taskValue(String column, Class<T> type) {
        return jdbcTemplate.queryForObject("SELECT " + column
                + " FROM plt_collection_task WHERE platform_task_id = ?", type, platformTaskId);
    }

    private <T> T batchValue(String column, Class<T> type) {
        return jdbcTemplate.queryForObject("SELECT " + column
                + " FROM plt_collection_batch WHERE id = ?", type, batchId);
    }

    private long count(String table, String column) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, platformTaskId);
    }

    private long countOutbox(String eventType) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_outbox_event "
                        + "WHERE aggregate_key = ? AND event_type = ?",
                Long.class, platformTaskId, eventType);
    }

    private void cleanFacts() {
        jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE aggregate_key = ?", platformTaskId);
        jdbcTemplate.update("DELETE FROM plt_collection_result_consumption WHERE platform_task_id = ?",
                platformTaskId);
        jdbcTemplate.update("DELETE FROM plt_collection_callback_record WHERE platform_task_id = ?",
                platformTaskId);
        jdbcTemplate.update("DELETE FROM plt_collection_task WHERE platform_task_id = ?", platformTaskId);
        jdbcTemplate.update("DELETE FROM plt_collection_batch WHERE id = ?", batchId);
    }

    private static long positiveLong(String value) {
        long hash = Integer.toUnsignedLong(value.hashCode());
        return 8_000_000_000_000_000_000L + hash;
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
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, CollectionCallbackService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-28T08:00:00Z"), ZoneOffset.UTC);
        }
    }
}
