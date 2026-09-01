package cn.iocoder.yudao.module.pms.cutover.service.plan.migration;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.migration.LegacyCutoverPlanReconciliationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
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
@SpringBootTest(classes = LegacyCutoverPlanReconciliationMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LegacyCutoverPlanReconciliationMySqlTest {

    @Resource JdbcTemplate jdbc;
    @Resource CutoverTaskMapper taskMapper;
    @Resource PlatformMigrationEvidenceApi migrationApi;
    @Resource LegacyCutoverPlanReconciliationService service;

    long tenantId;
    long taskId;
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
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        tenantId = 989_410_000_000L + suffix;
        taskId = 989_420_000_000L + suffix;
        TenantContextHolder.setTenantId(tenantId);
        CutoverTaskDO target = new CutoverTaskDO();
        target.setId(taskId);
        target.setTenantId(tenantId);
        target.setProjectId(989_430_000_000L + suffix);
        target.setTaskNo("LEGACY-CUT-41-" + suffix);
        target.setTaskName("旧割接任务映射");
        target.setScheduledTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        target.setTaskOrigin("LEGACY_FORWARD");
        target.setIntakeSourceType("LEGACY_FORWARD");
        target.setSourceSystem(null);
        target.setSourceBusinessNo(null);
        target.setTaskStatus("LEGACY_UNKNOWN");
        target.setLegacyTaskId(41L);
        target.setLegacyCutoverTypeRaw("REPLACE");
        target.setLegacyNetworkModeRaw("DUAL");
        target.setLegacyStatusValue(2);
        target.setLegacySourceVersion(6);
        target.setLegacyMappingVersion("FCUT002_LEGACY_V1");
        target.setVersion(0);
        target.setCreator("10");
        target.setUpdater("11");
        target.setCreateTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        target.setUpdateTime(LocalDateTime.of(2026, 8, 2, 10, 0));
        target.setDeleted(false);
        assertEquals(1, taskMapper.insert(target));

        String release = "fcut004-plan-" + suffix;
        MigrationBatchFact batch = migrationApi.createImportBatch(new CreateImportBatchCommand(
                tenantId, "CUT", "CUTOVER_PLAN_CURRENT_FORWARD", release, "NPDMS_LEGACY", "pms_cut_plan",
                "FCUT004_LEGACY_V1", 1, "a".repeat(64), LocalDateTime.now(), null, null,
                release + "-create", release + "-create"));
        batchId = batch.batchId();
        String sourcePayload = LegacyCutoverPlanRowConverterTest.legacyPayload(false)
                .replace("\"tenant_id\":1", "\"tenant_id\":" + tenantId);
        migrationApi.appendSourceRecord(new AppendMigrationSourceRecordCommand(tenantId, batchId,
                "NPDMS_LEGACY", "pms_cut_plan", "91", "PLAN-91",
                sourcePayload, "b".repeat(64), LocalDateTime.now(),
                release + "-source"));
        migrationApi.markStagedReady(new MarkStagedReadyCommand(tenantId, batchId, batch.version(),
                ImportStagingDecision.READY, 1L, "FCUT004_LEGACY_V1", "a".repeat(64), null,
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
        jdbc.update("DELETE FROM cut_step WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_plan_revision WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task WHERE tenant_id=?", tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void completesStagedSourceIntoLegacyRootStepsAndPlatformMapping() {
        LegacyCutoverPlanReconciliationResult result = service.reconcileNext(tenantId, "corr-fcut004-plan");

        assertEquals(new LegacyCutoverPlanReconciliationResult(true, batchId, 1, 0, 0), result);
        assertEquals(1, count("SELECT COUNT(*) FROM cut_plan_revision WHERE tenant_id=? AND cutover_task_id=? "
                + "AND origin_code='LEGACY_FORWARD' AND legacy_plan_id=91 AND revision_no=1", tenantId, taskId));
        assertEquals(4, count("SELECT COUNT(*) FROM cut_step WHERE tenant_id=?", tenantId));
        assertEquals(5, count("SELECT COUNT(*) FROM plt_external_key_mapping WHERE tenant_id=? AND batch_id=?",
                tenantId, batchId));
        assertEquals(1, count("SELECT COUNT(*) FROM plt_migration_batch WHERE tenant_id=? AND id=? "
                + "AND batch_status='COMPLETED' AND mapped_count=1 AND issue_count=0 AND retained_count=0",
                tenantId, batchId));
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
        for (Path directory = Path.of("").toAbsolutePath().normalize(); directory != null; directory = directory.getParent()) {
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
    @MapperScan({"cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration",
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
        @Bean LegacyCutoverPlanRowConverter converter() { return new LegacyCutoverPlanRowConverter(); }
        @Bean LegacyCutoverTaskMappingPort taskMappingPort(JdbcTemplate jdbc) {
            return (tenantId, legacyTaskId) -> jdbc.queryForObject(
                    "SELECT id FROM cut_task WHERE tenant_id=? AND legacy_task_id=? AND deleted=b'0'",
                    Long.class, tenantId, legacyTaskId);
        }
        @Bean LegacyCutoverPlanReconciliationService service(PlatformMigrationEvidenceApi migrationApi,
                                                              LegacyCutoverTaskMappingPort taskMappingPort,
                                                              LegacyCutoverPlanReconciliationMapper mapper,
                                                              CutoverPlanRevisionMapper planMapper,
                                                              CutoverPlanStepMapper stepMapper,
                                                              LegacyCutoverPlanRowConverter converter) {
            return new LegacyCutoverPlanReconciliationService(migrationApi, taskMappingPort, mapper,
                    planMapper, stepMapper, converter);
        }
    }
}
