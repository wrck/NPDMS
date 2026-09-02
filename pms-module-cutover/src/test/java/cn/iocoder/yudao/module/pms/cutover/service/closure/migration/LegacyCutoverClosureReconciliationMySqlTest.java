package cn.iocoder.yudao.module.pms.cutover.service.closure.migration;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendMigrationSourceRecordCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CreateImportBatchCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ImportStagingDecision;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MarkStagedReadyCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchFact;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.platform.service.migration.PlatformMigrationEvidenceApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.migration.PlatformMigrationEvidenceTransactionExecutor;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = LegacyCutoverClosureReconciliationMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LegacyCutoverClosureReconciliationMySqlTest {

    @Resource JdbcTemplate jdbc;
    @Resource PlatformMigrationEvidenceApi migrationApi;
    @Resource LegacyCutoverClosureReconciliationService service;

    long tenantId;
    long batchId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.cutover");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        tenantId = 989_610_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        TenantContextHolder.setTenantId(tenantId);
        String release = "fcut006-closure-" + tenantId;
        MigrationBatchFact batch = migrationApi.createImportBatch(new CreateImportBatchCommand(tenantId,
                "CUT", "CUTOVER_CLOSURE_CURRENT_FORWARD", release, "NPDMS_LEGACY", "pms_cut_execution",
                "FCUT006_LEGACY_V1", 1, "a".repeat(64), LocalDateTime.now(), null, null,
                release + "-create", release + "-create"));
        batchId = batch.batchId();
        migrationApi.appendSourceRecord(new AppendMigrationSourceRecordCommand(tenantId, batchId,
                "NPDMS_LEGACY", "pms_cut_execution", "301", "STEP-301",
                LegacyCutoverClosureRowClassifierTest.payload(tenantId), "b".repeat(64), LocalDateTime.now(),
                release + "-source"));
        migrationApi.markStagedReady(new MarkStagedReadyCommand(tenantId, batchId, batch.version(),
                ImportStagingDecision.READY, 1L, "FCUT006_LEGACY_V1", "a".repeat(64), null,
                release + "-stage", release + "-stage"));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM plt_external_key_mapping WHERE tenant_id=? AND batch_id=?", tenantId, batchId);
        jdbc.update("DELETE FROM plt_migration_issue WHERE tenant_id=? AND batch_id=?", tenantId, batchId);
        jdbc.update("DELETE FROM plt_migration_source_record WHERE tenant_id=? AND batch_id=?", tenantId, batchId);
        jdbc.update("DELETE FROM plt_migration_batch WHERE tenant_id=? AND id=?", tenantId, batchId);
        jdbc.update("DELETE FROM plt_operation_audit WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_idempotency_record WHERE tenant_id=?", tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void completesValidLegacyStepAsRetainedWithoutCreatingClosure() {
        var result = service.reconcileNext(tenantId, "corr-fcut006-closure");

        assertEquals(new LegacyCutoverClosureReconciliationService.Result(true, batchId, 0, 1), result);
        assertEquals(1, count("SELECT COUNT(*) FROM plt_external_key_mapping WHERE tenant_id=? AND batch_id=? "
                + "AND result_type='RETAINED'", tenantId, batchId));
        assertEquals(1, count("SELECT COUNT(*) FROM plt_migration_batch WHERE tenant_id=? AND id=? "
                + "AND batch_status='COMPLETED' AND source_count=1 AND mapped_count=0 "
                + "AND issue_count=0 AND retained_count=1", tenantId, batchId));
        assertEquals(0, count("SELECT COUNT(*) FROM cut_cutover_closure WHERE tenant_id=?", tenantId));
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
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
                values.putIfAbsent(value.substring(0, separator).trim(), unquote(value.substring(separator + 1).trim()));
            }
            return values;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取隔离数据库配置", exception);
        }
    }

    private static Path findRepositoryDotenv() {
        for (Path directory = Path.of("").toAbsolutePath().normalize(); directory != null;
             directory = directory.getParent()) {
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
    @EnableTransactionManagement(proxyTargetClass = true)
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
        @Bean LegacyCutoverClosureRowClassifier classifier() { return new LegacyCutoverClosureRowClassifier(); }
        @Bean LegacyCutoverClosureReconciliationService service(PlatformMigrationEvidenceApi migrationApi,
                                                                 LegacyCutoverClosureRowClassifier classifier) {
            return new LegacyCutoverClosureReconciliationService(migrationApi, classifier);
        }
    }
}
