package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
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
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = PlatformMigrationEvidenceMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PlatformMigrationEvidenceMySqlTest {

    private static final long TENANT_ID = 7L;
    private static final String SHA256 = "a".repeat(64);

    @Resource PlatformMigrationEvidenceApi api;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource TransactionTemplate transactionTemplate;

    private String prefix;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> env = environment();
        String database = env.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = env.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8&nullCatalogMeansCurrent=true");
        registry.add("spring.datasource.username", () -> required(env, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(env, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.platform");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        prefix = "fcom001-plt-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        List<Long> batches = jdbcTemplate.queryForList(
                "SELECT id FROM plt_migration_batch WHERE tenant_id=? AND release_id LIKE ?",
                Long.class, TENANT_ID, prefix + "%");
        for (Long batchId : batches) {
            jdbcTemplate.update("DELETE FROM plt_external_key_mapping WHERE tenant_id=? AND batch_id=?",
                    TENANT_ID, batchId);
            jdbcTemplate.update("DELETE FROM plt_migration_issue WHERE tenant_id=? AND batch_id=?",
                    TENANT_ID, batchId);
            jdbcTemplate.update("DELETE FROM plt_migration_source_record WHERE tenant_id=? AND batch_id=?",
                    TENANT_ID, batchId);
            jdbcTemplate.update("DELETE FROM plt_migration_batch WHERE tenant_id=? AND id=?", TENANT_ID, batchId);
        }
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=? AND correlation_id LIKE ?",
                TENANT_ID, prefix + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=? AND idempotency_key LIKE ?",
                TENANT_ID, prefix + "%");
        TenantContextHolder.clear();
    }

    @Test
    void completesLifecycleWithReplayAuditAndAppendOnlyIssueClosure() {
        MigrationBatchFact created = createBatch(prefix + "-lifecycle", 3);
        List<MigrationSourceRecordFact> sources = appendSources(created.batchId(), 3);
        MigrationBatchFact staged = stage(created, 3);

        MigrationBatchFact completed = transactionTemplate.execute(status -> {
            MigrationBatchClaimResult claim = api.claimStagedBatch(claimCommand());
            assertTrue(claim.claimed());
            api.appendExternalMapping(mappingCommand(claim.batch().batchId(), sources.get(0).sourceRecordId(),
                    prefix + "-map"));
            api.appendMigrationIssue(issueCommand(claim.batch().batchId(), sources.get(1).sourceRecordId(),
                    prefix + "-issue"));
            api.appendExternalMapping(retainedCommand(claim.batch().batchId(), sources.get(2).sourceRecordId(),
                    prefix + "-retain"));
            return api.completeReconciliation(new CompleteReconciliationCommand(
                    TENANT_ID, claim.batch().batchId(), claim.batch().version(),
                    3, 1, 1, 1, "rules-v1", prefix + "-complete", prefix + "-complete"));
        });

        assertNotNull(completed);
        assertEquals(MigrationBatchStatus.COMPLETED, completed.status());
        assertEquals(3, completed.sourceCount());
        assertEquals(1, completed.mappedCount());
        assertEquals(1, completed.issueCount());
        assertEquals(1, completed.retainedCount());
        SourceReconciliationResult replay = api.appendExternalMapping(
                mappingCommand(created.batchId(), sources.get(0).sourceRecordId(), prefix + "-map"));
        assertEquals(1, replay.mappingIds().size());
        Long issueId = jdbcTemplate.queryForObject(
                "SELECT id FROM plt_migration_issue WHERE tenant_id=? AND batch_id=?",
                Long.class, TENANT_ID, created.batchId());
        MigrationIssueFact closed = api.closeMigrationIssue(new CloseMigrationIssueCommand(
                TENANT_ID, issueId, 9L, "rules-v2", "{\"result\":\"retained\"}",
                prefix + "-close", prefix + "-close"));
        assertEquals(MigrationIssueStatus.CLOSED, closed.status());
        assertEquals(7L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? AND correlation_id LIKE ?",
                Long.class, TENANT_ID, prefix + "%"));
    }

    @Test
    void outerFailureRollsBackClaimClassificationAndPlatformFacts() {
        MigrationBatchFact created = createBatch(prefix + "-rollback", 1);
        MigrationSourceRecordFact source = appendSources(created.batchId(), 1).getFirst();
        stage(created, 1);

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            MigrationBatchClaimResult claim = api.claimStagedBatch(claimCommand());
            api.appendExternalMapping(mappingCommand(
                    claim.batch().batchId(), source.sourceRecordId(), prefix + "-rollback-map"));
            throw new IllegalStateException("rollback evidence");
        }));

        assertEquals("STAGED_READY", jdbcTemplate.queryForObject(
                "SELECT batch_status FROM plt_migration_batch WHERE tenant_id=? AND id=?",
                String.class, TENANT_ID, created.batchId()));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_external_key_mapping WHERE tenant_id=? AND batch_id=?",
                Long.class, TENANT_ID, created.batchId()));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND idempotency_key=?",
                Long.class, TENANT_ID, prefix + "-rollback-map"));
    }

    @Test
    void claimRequiresCallerTransactionAndLeavesStagedBatchUntouched() {
        MigrationBatchFact created = createBatch(prefix + "-mandatory", 0);
        stage(created, 0);

        cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException error =
                assertThrows(cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException.class,
                        () -> api.claimStagedBatch(claimCommand()));

        assertEquals(cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException.Code
                .CALLER_TRANSACTION_REQUIRED, error.getCode());
        assertEquals("STAGED_READY", jdbcTemplate.queryForObject(
                "SELECT batch_status FROM plt_migration_batch WHERE tenant_id=? AND id=?",
                String.class, TENANT_ID, created.batchId()));
    }

    @Test
    void concurrentClaimsNeverReturnTheSameBatch() throws Exception {
        stage(createBatch(prefix + "-concurrent-a", 0), 0);
        stage(createBatch(prefix + "-concurrent-b", 0), 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> task = () -> {
                TenantContextHolder.setTenantId(TENANT_ID);
                try {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return transactionTemplate.execute(status -> {
                        MigrationBatchClaimResult result = api.claimStagedBatch(claimCommand());
                        return result.claimed() ? result.batch().batchId() : null;
                    });
                } finally {
                    TenantContextHolder.clear();
                }
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Long firstId = first.get(10, TimeUnit.SECONDS);
            Long secondId = second.get(10, TimeUnit.SECONDS);
            List<Long> claimedIds = java.util.stream.Stream.of(firstId, secondId)
                    .filter(java.util.Objects::nonNull).toList();
            assertFalse(claimedIds.isEmpty());
            assertEquals(claimedIds.size(), claimedIds.stream().distinct().count());
            assertEquals((long) claimedIds.size(), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plt_migration_batch WHERE tenant_id=? AND release_id LIKE ? "
                            + "AND batch_status='RECONCILING'",
                    Long.class, TENANT_ID, prefix + "-concurrent-%"));
        }
    }

    private MigrationBatchFact createBatch(String releaseId, int rows) {
        return api.createImportBatch(new CreateImportBatchCommand(
                TENANT_ID, "COM", "F-COM-001", releaseId, "ERP", "orders", "schema-v1",
                rows, SHA256, LocalDateTime.now(), null, null, releaseId + "-create", releaseId + "-create"));
    }

    private List<MigrationSourceRecordFact> appendSources(Long batchId, int count) {
        java.util.ArrayList<MigrationSourceRecordFact> result = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(api.appendSourceRecord(new AppendMigrationSourceRecordCommand(
                    TENANT_ID, batchId, "ERP", "orders", "PK-" + index, "BK-" + index,
                    "{\"index\":" + index + "}", Character.toString((char) ('b' + index)).repeat(64),
                    LocalDateTime.now(), prefix + "-source-" + index)));
        }
        return result;
    }

    private MigrationBatchFact stage(MigrationBatchFact batch, int rows) {
        return api.markStagedReady(new MarkStagedReadyCommand(
                TENANT_ID, batch.batchId(), batch.version(), ImportStagingDecision.READY,
                (long) rows, "schema-v1", SHA256, null,
                prefix + "-stage-" + batch.batchId(), prefix + "-stage-" + batch.batchId()));
    }

    private ClaimStagedBatchCommand claimCommand() {
        return new ClaimStagedBatchCommand(TENANT_ID, "COM", "F-COM-001",
                List.of("ERP"), List.of("orders"), prefix + "-claim");
    }

    private AppendExternalMappingCommand mappingCommand(Long batchId, Long sourceId, String key) {
        return new AppendExternalMappingCommand(TENANT_ID, batchId, sourceId,
                SourceReconciliationType.MAPPED,
                List.of(new ExternalTargetMapping("COM", "ORDER", "com_sales_order", 88L,
                        "PRIMARY", 0)), key, key);
    }

    private AppendExternalMappingCommand retainedCommand(Long batchId, Long sourceId, String key) {
        return new AppendExternalMappingCommand(TENANT_ID, batchId, sourceId,
                SourceReconciliationType.RETAINED, List.of(), key, key);
    }

    private AppendMigrationIssueCommand issueCommand(Long batchId, Long sourceId, String key) {
        return new AppendMigrationIssueCommand(TENANT_ID, batchId, sourceId,
                "ISSUE-1", "MISSING_QUANTITY", "BK-1", List.of(88L), "{\"raw\":1}", key, key);
    }

    private static Map<String, String> environment() {
        Map<String, String> values = new LinkedHashMap<>(System.getenv());
        Path dotenv = findRepositoryDotenv();
        if (dotenv == null) return values;
        try {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#") || !value.contains("=")) continue;
                int separator = value.indexOf('=');
                values.putIfAbsent(value.substring(0, separator).trim(),
                        unquote(value.substring(separator + 1).trim()));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取隔离数据库配置", ex);
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
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + key);
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.platform.dal.mysql.migration",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class,
            PlatformMigrationEvidenceApiImpl.class, PlatformMigrationEvidenceTransactionExecutor.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean TransactionTemplate transactionTemplate(org.springframework.transaction.PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }
}
