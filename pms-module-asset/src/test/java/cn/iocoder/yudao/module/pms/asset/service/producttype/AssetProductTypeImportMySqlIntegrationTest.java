package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.DeviceCurrentProductTypeInput;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.DeviceCurrentProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.AuthorizedDeviceProductTypesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypesByCodesQuery;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.RecordAssetProductTypeSourceFailureCommand;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOperationAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOperationAuditMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = AssetProductTypeImportMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AssetProductTypeImportMySqlIntegrationTest {

    @Resource private AssetProductTypeImportWriter importWriter;
    @Resource private AssetProductTypeConflictRecordService conflictRecordService;
    @Resource private AssetProductTypeSourceFailureWriter sourceFailureWriter;
    @Resource private PlatformCommandExecutionApi commandExecutionApi;
    @Resource private AssetProductTypeMapper productTypeMapper;
    @Resource private DeviceCurrentProductTypeMapper currentProductTypeMapper;
    @Resource private JdbcTemplate jdbcTemplate;

    private long idBase;
    private String keyPrefix;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> values = environment();
        String port = values.getOrDefault("NPDMS_MYSQL_PORT", "23316");
        String database = values.getOrDefault("NPDMS_DB_NAME", "npdms_test");
        if (!"npdms_test".equals(database)) {
            throw new IllegalStateException("EQP-01真实MySQL测试仅允许使用npdms_test隔离库");
        }
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(values, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(values, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.asset");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        idBase = 987_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 100L;
        keyPrefix = "it-fast002-" + UUID.randomUUID();
        TestApplication.auditFailureEnabled = false;
    }

    @AfterEach
    void tearDown() {
        try {
            TestApplication.auditFailureEnabled = false;
            jdbcTemplate.update("DELETE current_type FROM ast_device_current_product_type current_type "
                    + "LEFT JOIN ast_product_type_source_mapping mapping "
                    + "ON mapping.tenant_id=current_type.tenant_id AND mapping.id=current_type.source_mapping_id "
                    + "WHERE current_type.tenant_id=1 AND (current_type.source_version LIKE ? "
                    + "OR mapping.source_key LIKE ?)", keyPrefix + "%", keyPrefix + "%");
            jdbcTemplate.update("DELETE FROM ast_product_type_source_mapping WHERE tenant_id=1 AND source_key LIKE ?", keyPrefix + "%");
            jdbcTemplate.update("DELETE FROM ast_product_type WHERE tenant_id=1 AND source_key LIKE ?", keyPrefix + "%");
            jdbcTemplate.update("DELETE FROM ast_device WHERE id BETWEEN ? AND ?", idBase, idBase + 99);
            jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE correlation_id LIKE ?", keyPrefix + "%");
            jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?", keyPrefix + "%");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void schemaConstraintsReserveSoftDeletedCodeAndKeepOneCurrentDeviceReference() {
        LocalDateTime watermark = LocalDateTime.of(2026, 8, 31, 8, 0);
        long productTypeId = idBase + 1;
        long mappingId = idBase + 2;
        long deviceId = idBase + 3;
        String typeCode = "TYPE-SCHEMA-" + Long.toUnsignedString(idBase, 36);
        insertDevice(deviceId, 501L);
        insertProductType(productTypeId, typeCode, keyPrefix + "-schema", "v1", watermark, "a".repeat(64));
        insertMapping(mappingId, keyPrefix + "-schema", productTypeId, "v1", watermark, "a".repeat(64));
        jdbcTemplate.update("UPDATE ast_product_type SET deleted=b'1' WHERE id=?", productTypeId);

        assertThrows(RuntimeException.class, () -> insertProductType(idBase + 4, typeCode,
                keyPrefix + "-schema-other", "v2", watermark.plusMinutes(1), "b".repeat(64)));
        jdbcTemplate.update("UPDATE ast_product_type SET deleted=b'0' WHERE id=?", productTypeId);
        insertCurrent(idBase + 5, deviceId, productTypeId, typeCode, mappingId, "v1", watermark, null);
        assertThrows(RuntimeException.class, () -> insertCurrent(idBase + 6, deviceId, productTypeId,
                typeCode, mappingId, "v2", watermark.plusMinutes(1), null));

        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ast_device_current_product_type "
                        + "WHERE tenant_id=1 AND device_id=? AND current_marker=1", Long.class, deviceId));
    }

    @Test
    void controlledImportCreatesCopyMappingAndCurrentReferenceAndReplaysWithoutDuplicates() {
        long deviceId = idBase + 11;
        insertDevice(deviceId, 501L);
        ImportAssetProductTypeCommand command = command("positive", "TYPE-POSITIVE",
                LocalDateTime.of(2026, 8, 31, 9, 0),
                List.of(new DeviceCurrentProductTypeInput(deviceId, "RESOLVED")));

        PlatformCommandExecutionApi.ExecutionResult<ImportAssetProductTypeResult> first = execute(command);
        PlatformCommandExecutionApi.ExecutionResult<ImportAssetProductTypeResult> replay = execute(command);

        assertEquals(PlatformCommandExecutionApi.Decision.NEW, first.decision());
        assertEquals(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, replay.decision());
        assertEquals(first.response().productTypeId(), replay.response().productTypeId());
        assertEquals(1L, count("ast_product_type", "source_key", command.sourceKey()));
        assertEquals(1L, count("ast_product_type_source_mapping", "source_key", command.sourceKey()));
        assertEquals(1L, count("ast_device_current_product_type", "source_version", command.sourceVersion()));
        assertEquals(1L, count("plt_idempotency_record", "idempotency_key", command.idempotencyKey()));
        assertEquals(1L, count("plt_operation_audit", "correlation_id", command.operationId()));
    }

    @Test
    void staleSourceDoesNotOverwriteLastSuccessfulFacts() {
        LocalDateTime currentWatermark = LocalDateTime.of(2026, 8, 31, 10, 0);
        ImportAssetProductTypeCommand current = command("stale", "TYPE-STALE", currentWatermark, List.of());
        ImportAssetProductTypeResult imported = execute(current).response();
        ImportAssetProductTypeCommand stale = new ImportAssetProductTypeCommand(
                keyPrefix + "-stale-old-op", keyPrefix + "-stale-old-idem", "TYPE-STALE", "旧名称", false,
                "CRM", current.sourceKey(), "old-version", currentWatermark.minusMinutes(1), "c".repeat(64), List.of());

        assertThrows(AssetProductTypeImportRejectedException.class,
                () -> importWriter.importOnce(1L, 9L, stale));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT display_name,enabled,source_version,source_updated_at,payload_hash "
                        + "FROM ast_product_type WHERE tenant_id=1 AND source_key=?", current.sourceKey());
        assertEquals("集成测试类型", stored.get("display_name"));
        assertTrue((Boolean) stored.get("enabled"));
        assertEquals(current.sourceVersion(), stored.get("source_version"));
        assertEquals(currentWatermark, stored.get("source_updated_at"));
        assertEquals(current.payloadHash(), stored.get("payload_hash"));
        Map<String, Object> mapping = jdbcTemplate.queryForMap(
                "SELECT product_type_id,source_version,source_updated_at,payload_hash "
                        + "FROM ast_product_type_source_mapping WHERE tenant_id=1 AND source_key=?",
                current.sourceKey());
        assertEquals(imported.productTypeId(), mapping.get("product_type_id"));
        assertEquals(current.sourceVersion(), mapping.get("source_version"));
        assertEquals(currentWatermark, mapping.get("source_updated_at"));
        assertEquals(current.payloadHash(), mapping.get("payload_hash"));
    }

    @Test
    void disabledProductTypeRemainsQueryableButIsNotEnabledForNewConsumption() {
        ImportAssetProductTypeCommand disabled = new ImportAssetProductTypeCommand(
                keyPrefix + "-disabled-op", keyPrefix + "-disabled-idem", "TYPE-DISABLED", "停用类型", false,
                "CRM", keyPrefix + "-disabled", "v1", LocalDateTime.of(2026, 8, 31, 11, 0),
                "d".repeat(64), List.of());
        execute(disabled);

        var stored = productTypeMapper.selectByCodes(new ProductTypesByCodesQuery(1L, Set.of("TYPE-DISABLED")));

        assertEquals(1, stored.size());
        assertFalse(stored.getFirst().getEnabled());
        assertFalse(stored.getFirst().getDeleted());
        assertEquals("v1", stored.getFirst().getSourceVersion());
    }

    @Test
    void authorizedDeviceProjectionOnlyReturnsVisibleProjectDevice() {
        LocalDateTime watermark = LocalDateTime.of(2026, 8, 31, 12, 0);
        long visibleDeviceId = idBase + 21;
        long hiddenDeviceId = idBase + 22;
        insertDevice(visibleDeviceId, 501L);
        insertDevice(hiddenDeviceId, 502L);
        ImportAssetProductTypeCommand command = command("scope", "TYPE-SCOPE", watermark,
                List.of(new DeviceCurrentProductTypeInput(visibleDeviceId, "RESOLVED"),
                        new DeviceCurrentProductTypeInput(hiddenDeviceId, "RESOLVED")));
        execute(command);

        var projections = currentProductTypeMapper.selectAuthorizedCurrent(
                new AuthorizedDeviceProductTypesQuery(1L, Set.of(visibleDeviceId, hiddenDeviceId),
                        Set.of(501L), watermark.plusMinutes(1)));

        assertEquals(1, projections.size());
        assertEquals(visibleDeviceId, projections.getFirst().deviceId());
        assertEquals("TYPE-SCOPE", projections.getFirst().productTypeCode());
    }

    @Test
    void sourceFailureKeepsLastSuccessfulValuesAndTimestamp() {
        LocalDateTime watermark = LocalDateTime.of(2026, 8, 31, 13, 0);
        ImportAssetProductTypeCommand command = command("degraded", "TYPE-DEGRADED", watermark, List.of());
        ImportAssetProductTypeResult imported = execute(command).response();
        LocalDateTime syncedAt = jdbcTemplate.queryForObject(
                "SELECT synced_at FROM ast_product_type WHERE id=?", LocalDateTime.class, imported.productTypeId());

        sourceFailureWriter.markFailed(1L, 9L, new RecordAssetProductTypeSourceFailureCommand(
                keyPrefix + "-degraded-failure", "CRM", command.sourceKey(), "TIMEOUT"));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT display_name,source_version,source_updated_at,synced_at,sync_status "
                        + "FROM ast_product_type WHERE id=?", imported.productTypeId());
        assertEquals("集成测试类型", stored.get("display_name"));
        assertEquals(command.sourceVersion(), stored.get("source_version"));
        assertEquals(watermark, stored.get("source_updated_at"));
        assertEquals(syncedAt, stored.get("synced_at"));
        assertEquals("FAILED", stored.get("sync_status"));
    }

    @Test
    void crossTenantDeviceRollsBackPreparedProductTypeAndMapping() {
        ImportAssetProductTypeCommand command = command("cross", "TYPE-CROSS", LocalDateTime.of(2026, 8, 31, 9, 0),
                List.of(new DeviceCurrentProductTypeInput(idBase + 1, "RESOLVED")));

        assertThrows(AssetProductTypeImportRejectedException.class,
                () -> importWriter.importOnce(1L, 9L, command));

        assertEquals(0L, count("ast_product_type", "source_key", command.sourceKey()));
        assertEquals(0L, count("ast_product_type_source_mapping", "source_key", command.sourceKey()));
        assertEquals(0L, count("ast_device_current_product_type", "source_version", command.sourceVersion()));
    }

    @Test
    void rejectedConflictRollsBackBusinessTransactionAndCommitsConflictEvidence() {
        LocalDateTime watermark = LocalDateTime.of(2026, 8, 31, 9, 0);
        long productTypeId = idBase + 11;
        long mappingId = idBase + 12;
        insertProductType(productTypeId, "TYPE-CONFLICT", keyPrefix + "-conflict", "v1", watermark, "a".repeat(64));
        insertMapping(mappingId, keyPrefix + "-conflict", productTypeId, "v1", watermark, "a".repeat(64));
        ImportAssetProductTypeCommand command = command("conflict", "TYPE-CONFLICT", watermark, List.of());

        AssetProductTypeImportRejectedException rejection = assertThrows(
                AssetProductTypeImportRejectedException.class,
                () -> commandExecutionApi.execute(scope(command), "b".repeat(64), ImportAssetProductTypeResult.class,
                        () -> importWriter.importOnce(1L, 9L, command), result -> successFacts(command, result)));
        conflictRecordService.record(1L, 9L, rejection);

        assertEquals(0L, count("plt_idempotency_record", "idempotency_key", command.idempotencyKey()));
        assertEquals("CONFLICT", jdbcTemplate.queryForObject(
                "SELECT mapping_status FROM ast_product_type_source_mapping WHERE id=?", String.class, mappingId));
        assertEquals("v1", jdbcTemplate.queryForObject(
                "SELECT source_version FROM ast_product_type_source_mapping WHERE id=?", String.class, mappingId));
        assertEquals(1L, count("plt_operation_audit", "correlation_id", command.operationId()));
    }

    @Test
    void sourceFailureAuditFailureRollsBackFailedStatus() {
        LocalDateTime watermark = LocalDateTime.of(2026, 8, 31, 9, 0);
        long productTypeId = idBase + 21;
        insertProductType(productTypeId, "TYPE-FAILURE", keyPrefix + "-failure", "v1", watermark, "a".repeat(64));
        insertMapping(idBase + 22, keyPrefix + "-failure", productTypeId, "v1", watermark, "a".repeat(64));
        TestApplication.auditFailureEnabled = true;
        RecordAssetProductTypeSourceFailureCommand command = new RecordAssetProductTypeSourceFailureCommand(
                keyPrefix + "-failure-op", "CRM", keyPrefix + "-failure", "TIMEOUT");

        assertThrows(RuntimeException.class, () -> sourceFailureWriter.markFailed(1L, 9L, command));

        assertEquals("FRESH", jdbcTemplate.queryForObject(
                "SELECT sync_status FROM ast_product_type WHERE id=?", String.class, productTypeId));
        assertEquals(0L, count("plt_operation_audit", "correlation_id", command.operationId()));
    }

    @Test
    void successAuditFailureRollsBackAllImportAndIdempotencyFacts() {
        long deviceId = idBase + 31;
        insertDevice(deviceId);
        ImportAssetProductTypeCommand command = command("success-audit", "TYPE-AUDIT",
                LocalDateTime.of(2026, 8, 31, 10, 0),
                List.of(new DeviceCurrentProductTypeInput(deviceId, "RESOLVED")));
        assertThrows(RuntimeException.class,
                () -> commandExecutionApi.execute(scope(command), "c".repeat(64), ImportAssetProductTypeResult.class,
                        () -> importWriter.importOnce(1L, 9L, command), result -> invalidSuccessFacts(command, result)));

        assertEquals(0L, count("ast_product_type", "source_key", command.sourceKey()));
        assertEquals(0L, count("ast_product_type_source_mapping", "source_key", command.sourceKey()));
        assertEquals(0L, count("ast_device_current_product_type", "source_version", command.sourceVersion()));
        assertEquals(0L, count("plt_idempotency_record", "idempotency_key", command.idempotencyKey()));
        assertEquals(0L, count("plt_operation_audit", "correlation_id", command.operationId()));
    }

    private ImportAssetProductTypeCommand command(String suffix, String code, LocalDateTime sourceUpdatedAt,
                                                   List<DeviceCurrentProductTypeInput> devices) {
        return new ImportAssetProductTypeCommand(keyPrefix + "-" + suffix + "-op", keyPrefix + "-" + suffix + "-idem",
                code, "集成测试类型", true, "CRM", keyPrefix + "-" + suffix,
                keyPrefix + "-" + suffix + "-version", sourceUpdatedAt, "b".repeat(64), devices);
    }

    private PlatformCommandExecutionApi.IdempotencyScope scope(ImportAssetProductTypeCommand command) {
        return new PlatformCommandExecutionApi.IdempotencyScope(
                1L, AssetProductTypeImportService.IMPORT_SCOPE, 9L, command.idempotencyKey());
    }

    private PlatformCommandExecutionApi.ExecutionResult<ImportAssetProductTypeResult> execute(
            ImportAssetProductTypeCommand command) {
        return commandExecutionApi.execute(scope(command), command.payloadHash(), ImportAssetProductTypeResult.class,
                () -> importWriter.importOnce(1L, 9L, command), result -> successFacts(command, result));
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            ImportAssetProductTypeCommand command, ImportAssetProductTypeResult result) {
        return new PlatformCommandExecutionApi.SuccessFacts(
                AssetProductTypeImportService.IMPORT_OPERATION, "AssetProductType",
                String.valueOf(result.productTypeId()), command.operationId(), "{}", null, null);
    }

    private PlatformCommandExecutionApi.SuccessFacts invalidSuccessFacts(
            ImportAssetProductTypeCommand command, ImportAssetProductTypeResult result) {
        return new PlatformCommandExecutionApi.SuccessFacts(
                AssetProductTypeImportService.IMPORT_OPERATION, "AssetProductType",
                String.valueOf(result.productTypeId()), command.operationId(), null, null, null);
    }

    private void insertProductType(long id, String code, String sourceKey, String sourceVersion,
                                   LocalDateTime sourceUpdatedAt, String payloadHash) {
        jdbcTemplate.update("INSERT INTO ast_product_type "
                        + "(id,type_code,display_name,enabled,source_system,source_key,source_version,source_updated_at,"
                        + "payload_hash,sync_status,last_sync_attempt_at,synced_at,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,b'1','CRM',?,?,?,?, 'FRESH',?,?,0,'mysql-it','mysql-it',b'0',1)",
                id, code, "集成测试类型", sourceKey, sourceVersion, sourceUpdatedAt, payloadHash,
                sourceUpdatedAt, sourceUpdatedAt);
    }

    private void insertMapping(long id, String sourceKey, long productTypeId, String sourceVersion,
                               LocalDateTime sourceUpdatedAt, String payloadHash) {
        jdbcTemplate.update("INSERT INTO ast_product_type_source_mapping "
                        + "(id,source_system,source_key,source_version,source_updated_at,payload_hash,product_type_id,"
                        + "mapping_status,synced_at,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,'CRM',?,?,?,?,?,'RESOLVED',?,0,'mysql-it','mysql-it',b'0',1)",
                id, sourceKey, sourceVersion, sourceUpdatedAt, payloadHash, productTypeId, sourceUpdatedAt);
    }

    private void insertDevice(long id) {
        insertDevice(id, null);
    }

    private void insertDevice(long id, Long projectId) {
        jdbcTemplate.update("INSERT INTO ast_device "
                        + "(id,sn,name,project_id,project_assignment_version,status,source_system,source_key,sync_status,"
                        + "version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,?,0,'ACTIVE','PMS',?,'FRESH',0,'mysql-it','mysql-it',b'0',1)",
                id, "IT-FAST002-" + id, "FAST002", projectId, keyPrefix + "-device-" + id);
    }

    private void insertCurrent(long id, long deviceId, long productTypeId, String productTypeCode,
                               long mappingId, String sourceVersion, LocalDateTime sourceUpdatedAt,
                               LocalDateTime effectiveTo) {
        jdbcTemplate.update("INSERT INTO ast_device_current_product_type "
                        + "(id,device_id,product_type_id,product_type_code,source_mapping_id,resolution_status,"
                        + "source_version,source_updated_at,effective_from,effective_to,version,creator,updater,deleted,"
                        + "tenant_id) VALUES (?,?,?,?,?,'RESOLVED',?,?,?, ?,0,'mysql-it','mysql-it',b'0',1)",
                id, deviceId, productTypeId, productTypeCode, mappingId, sourceVersion, sourceUpdatedAt,
                sourceUpdatedAt, effectiveTo);
    }

    private long count(String table, String column, String value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=1 AND " + column + "=?", Long.class, value);
    }

    static Map<String, String> environment() {
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

    static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("真实MySQL集成测试缺少当前仓库参数：" + key);
        }
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype",
            "cn.iocoder.yudao.module.pms.asset.dal.mysql.device",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            AssetProductTypeSourceOrder.class,
            AssetProductTypeImportWriter.class,
            AssetProductTypeAuditService.class, AssetProductTypeConflictRecordService.class,
            AssetProductTypeSourceFailureWriter.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        private static volatile boolean auditFailureEnabled;

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor(MybatisPlusInterceptor interceptor) {
            OptimisticLockerInnerInterceptor optimisticLocker = new OptimisticLockerInnerInterceptor();
            interceptor.addInnerInterceptor(optimisticLocker);
            return optimisticLocker;
        }

        @Bean
        OperationAuditApi operationAuditApi(PlatformOperationAuditMapper auditMapper) {
            return new OperationAuditApi() {
                @Override
                public void record(Long tenantId, Long actorId, String correlationId, String operationCode,
                                   Long requestId, String resultCode, Map<String, ?> safeDetail) {
                    record(tenantId, actorId, correlationId, operationCode, "ProjectSplitRequest",
                            String.valueOf(requestId), resultCode, safeDetail);
                }

                @Override
                public void record(Long tenantId, Long actorId, String correlationId, String operationCode,
                                   String aggregateType, String aggregateKey, String resultCode,
                                   Map<String, ?> safeDetail) {
                    if (auditFailureEnabled) {
                        throw new IllegalStateException("F-AST-002 injected audit failure");
                    }
                    PlatformOperationAuditDO audit = new PlatformOperationAuditDO();
                    audit.setTenantId(tenantId);
                    audit.setOperationCode(operationCode);
                    audit.setAggregateType(aggregateType);
                    audit.setAggregateKey(aggregateKey);
                    audit.setActorId(actorId);
                    audit.setCorrelationId(correlationId);
                    audit.setIdempotencyKeyDigest("d".repeat(64));
                    audit.setResultCode(resultCode);
                    audit.setDetailSnapshot("{}");
                    audit.setOccurredAt(LocalDateTime.now());
                    audit.setCreateTime(audit.getOccurredAt());
                    auditMapper.insert(audit);
                }
            };
        }
    }
}
