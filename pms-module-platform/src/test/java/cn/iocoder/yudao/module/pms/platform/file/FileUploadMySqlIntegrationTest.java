package cn.iocoder.yudao.module.pms.platform.file;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageStoreCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.BoundedMultipartReader;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileContentPolicyService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleteCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = FileUploadMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FileUploadMySqlIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4 task5".getBytes(StandardCharsets.US_ASCII);
    private static final String SHA = "a".repeat(64);

    @Resource FileUploadApplicationService service;
    @Resource FileBusinessObjectPolicyRegistry policyRegistry;
    @Resource BoundedMultipartReader multipartReader;
    @Resource FileContentPolicyService contentPolicyService;
    @Resource FileStorageReceiptApi storageReceiptApi;
    @Resource JdbcTemplate jdbcTemplate;

    private String objectId;
    private String keyPrefix;
    private String initKey;
    private String completeKey;
    private Long artifactId;

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
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        objectId = "it-fplt001-task5-" + UUID.randomUUID();
        keyPrefix = "task5-" + UUID.randomUUID() + "-";
        initKey = key("init");
        completeKey = key("complete");
        reset(policyRegistry, contentPolicyService, storageReceiptApi);
        when(policyRegistry.inspect(any())).thenReturn(policy());
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(policy());
        when(contentPolicyService.validateBounded(any())).thenReturn(
                new ValidatedFileContent(PDF, PDF.length, SHA, "application/pdf", ".pdf", "CLAMAV", "1"));
        AtomicLong infraFileId = new AtomicLong(8_900_000L);
        when(storageReceiptApi.store(any())).thenAnswer(invocation -> {
            FileStorageStoreCommand command = invocation.getArgument(0);
            return new FileStorageReceipt(command.storageOperationId(), infraFileId.incrementAndGet(),
                    command.name(), command.mediaType(), command.validatedContent().length);
        });
    }

    @AfterEach
    void tearDown() {
        if (artifactId != null) {
            jdbcTemplate.update("DELETE FROM plt_file_upload_session WHERE tenant_id=0 AND artifact_id=?", artifactId);
            jdbcTemplate.update("DELETE FROM plt_file_reference WHERE tenant_id=0 AND artifact_id=?", artifactId);
            jdbcTemplate.update("DELETE FROM plt_file_version WHERE tenant_id=0 AND artifact_id=?", artifactId);
            jdbcTemplate.update("DELETE FROM plt_file_artifact WHERE tenant_id=0 AND id=?", artifactId);
        }
        jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE tenant_id=0 AND aggregate_key=?",
                artifactId == null ? "NONE" : String.valueOf(artifactId));
        if (artifactId != null) {
            jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=0 AND aggregate_key=?",
                    String.valueOf(artifactId));
        }
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=0 AND idempotency_key LIKE ?",
                keyPrefix + "%");
    }

    @Test
    void commitsAndReplaysFirstUploadWithOneAuditAndTwoEvents() {
        var initialized = service.initialize(new FileUploadInitializeCommand(0L, 9L, initKey,
                "CREATE_ARTIFACT", null, null, "SOL", "DURATION_CHANGE", objectId,
                "CUSTOMER_EVIDENCE", "slot-a", "evidence.pdf", "CUSTOMER_EVIDENCE",
                (long) PDF.length, "application/pdf", null));
        artifactId = initialized.artifactId();
        var file = new MockMultipartFile("file", "evidence.pdf", "application/pdf", PDF);

        var first = service.complete(new FileUploadCompleteCommand(
                0L, 9L, completeKey, artifactId, initialized.sessionId(), file, null));
        var replay = service.complete(new FileUploadCompleteCommand(
                0L, 9L, completeKey, artifactId, initialized.sessionId(), file, null));

        assertEquals(first, replay);
        assertEquals(1L, count("plt_file_artifact", "id", artifactId));
        assertEquals(1L, count("plt_file_version", "artifact_id", artifactId));
        assertEquals(1L, count("plt_file_reference", "artifact_id", artifactId));
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT status_code FROM plt_file_upload_session WHERE tenant_id=0 AND id=?",
                String.class, initialized.sessionId()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=0 AND correlation_id=? "
                        + "AND operation_code='FILE_UPLOAD_COMPLETE' AND result_code='SUCCESS'",
                Long.class, String.valueOf(initialized.sessionId())));
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=0 AND aggregate_key=? "
                        + "AND event_type IN ('FileVersionCommitted','FileReferenceAttached')",
                Long.class, String.valueOf(artifactId)));
        var ids = jdbcTemplate.queryForList(
                "SELECT event_id, JSON_UNQUOTE(JSON_EXTRACT(payload,'$.eventId')) payload_event_id "
                        + "FROM plt_outbox_event WHERE tenant_id=0 AND aggregate_key=?",
                String.valueOf(artifactId));
        assertEquals(2, ids.size());
        ids.forEach(row -> assertEquals(row.get("event_id"), row.get("payload_event_id")));
        assertNotNull(first.referenceId());
    }

    @Test
    void addsImmutableVersionsAndAllowsOnlyOneConcurrentReferenceCas() throws Exception {
        var initialized = initialize("CREATE_ARTIFACT", null, null, key("initial-init"));
        artifactId = initialized.artifactId();
        var first = complete(initialized, key("initial-complete"), PDF);
        Long firstInfraFileId = jdbcTemplate.queryForObject(
                "SELECT infra_file_id FROM plt_file_version WHERE tenant_id=0 AND artifact_id=? AND version_no=1",
                Long.class, artifactId);

        var replacement = initialize("ADD_VERSION", artifactId, 0, key("replace-init"));
        String replacementKey = key("replace-complete");
        var second = complete(replacement, replacementKey, PDF);
        assertEquals(second, complete(replacement, replacementKey, PDF));
        assertThrows(RuntimeException.class, () -> complete(replacement, replacementKey,
                "%PDF-1.4 changed".getBytes(StandardCharsets.US_ASCII)));

        assertEquals(2, second.versionNo());
        assertEquals(2L, count("plt_file_version", "artifact_id", artifactId));
        assertEquals(firstInfraFileId, jdbcTemplate.queryForObject(
                "SELECT infra_file_id FROM plt_file_version WHERE tenant_id=0 AND artifact_id=? AND version_no=1",
                Long.class, artifactId));
        assertEquals(SHA, jdbcTemplate.queryForObject(
                "SELECT sha256 FROM plt_file_version WHERE tenant_id=0 AND artifact_id=? AND version_no=1",
                String.class, artifactId));
        assertReference(2, 1);

        var contenderA = initialize("ADD_VERSION", artifactId, 1, key("race-a-init"));
        var contenderB = initialize("ADD_VERSION", artifactId, 1, key("race-b-init"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = executor.submit(() -> raceComplete(contenderA, key("race-a-complete"), ready, start));
            Future<Boolean> b = executor.submit(() -> raceComplete(contenderB, key("race-b-complete"), ready, start));
            ready.await();
            start.countDown();
            assertEquals(1, (a.get() ? 1 : 0) + (b.get() ? 1 : 0));
        }

        assertEquals(3L, count("plt_file_version", "artifact_id", artifactId));
        assertReference(3, 2);
        assertEquals(6L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=0 AND aggregate_key=? "
                        + "AND event_type IN ('FileVersionCommitted','FileReferenceAttached')",
                Long.class, String.valueOf(artifactId)));
        assertEquals(first.referenceId(), second.referenceId());
    }

    private boolean raceComplete(cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized initialized,
                                 String key, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            complete(initialized, key, PDF);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized initialize(
            String mode, Long existingArtifactId, Integer expectedReferenceVersion, String key) {
        return service.initialize(new FileUploadInitializeCommand(0L, 9L, key,
                mode, existingArtifactId, expectedReferenceVersion, "SOL", "DURATION_CHANGE", objectId,
                "CUSTOMER_EVIDENCE", "slot-a", "evidence.pdf", "CUSTOMER_EVIDENCE",
                (long) PDF.length, "application/pdf", null));
    }

    private cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleted complete(
            cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized initialized,
            String key, byte[] content) {
        return service.complete(new FileUploadCompleteCommand(0L, 9L, key, initialized.artifactId(),
                initialized.sessionId(), new MockMultipartFile(
                "file", "evidence.pdf", "application/pdf", content), null));
    }

    private void assertReference(int versionNo, int referenceVersion) {
        Map<String, Object> reference = jdbcTemplate.queryForMap(
                "SELECT file_version_no, version FROM plt_file_reference WHERE tenant_id=0 AND artifact_id=?",
                artifactId);
        assertEquals(versionNo, ((Number) reference.get("file_version_no")).intValue());
        assertEquals(referenceVersion, ((Number) reference.get("version")).intValue());
    }

    private String key(String suffix) {
        return keyPrefix + suffix;
    }

    private long count(String table, String column, Object value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE tenant_id=0 AND " + column + "=?", Long.class, value);
    }

    private FileBusinessObjectPolicyFact policy() {
        return new FileBusinessObjectPolicyFact(true, 8L, "MUTABLE", "SINGLE",
                Set.of("CUSTOMER_EVIDENCE"), Set.of("application/pdf"), 52_428_800L, "INTERNAL");
    }

    private static Map<String, String> currentEnvironment() {
        Map<String, String> environment = new LinkedHashMap<>(System.getenv());
        Path envFile = findRepositoryDotenv();
        if (envFile == null) return environment;
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int separator = trimmed.indexOf('=');
                if (separator <= 0) continue;
                environment.putIfAbsent(trimmed.substring(0, separator).trim(),
                        unquote(trimmed.substring(separator + 1).trim()));
            }
            return environment;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取本地隔离数据库配置", exception);
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

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.platform.dal.mysql.file",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, OperationAuditApiImpl.class,
            FileUploadApplicationService.class, FileEventFactory.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean FileBusinessObjectPolicyRegistry policyRegistry() { return mock(FileBusinessObjectPolicyRegistry.class); }
        @Bean BoundedMultipartReader multipartReader() { return new BoundedMultipartReader(); }
        @Bean FileContentPolicyService contentPolicyService() { return mock(FileContentPolicyService.class); }
        @Bean FileStorageReceiptApi storageReceiptApi() { return mock(FileStorageReceiptApi.class); }
    }
}
