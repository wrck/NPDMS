package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.DeviceCurrentProductTypeInput;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = AssetProductTypeImportMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Timeout(45)
class AssetProductTypeConcurrencyMySqlTest {

    @Resource private AssetProductTypeImportWriter importWriter;
    @Resource private AssetProductTypeConflictRecordService conflictRecordService;
    @Resource private PlatformCommandExecutionApi commandExecutionApi;
    @Resource private JdbcTemplate jdbcTemplate;

    private long idBase;
    private String keyPrefix;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> values = AssetProductTypeImportMySqlIntegrationTest.environment();
        String port = values.getOrDefault("NPDMS_MYSQL_PORT", "23316");
        String database = values.getOrDefault("NPDMS_DB_NAME", "npdms_test");
        if (!"npdms_test".equals(database)) {
            throw new IllegalStateException("EQP-01真实MySQL测试仅允许使用npdms_test隔离库");
        }
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username",
                () -> AssetProductTypeImportMySqlIntegrationTest.required(values, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password",
                () -> AssetProductTypeImportMySqlIntegrationTest.required(values, "NPDMS_DB_PASSWORD"));
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
        idBase = 988_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 100L;
        keyPrefix = "it-fast002-concurrency-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE current_type FROM ast_device_current_product_type current_type "
                    + "LEFT JOIN ast_product_type_source_mapping mapping "
                    + "ON mapping.tenant_id=current_type.tenant_id AND mapping.id=current_type.source_mapping_id "
                    + "WHERE current_type.tenant_id=1 AND (current_type.source_version LIKE ? "
                    + "OR mapping.source_key LIKE ?)", keyPrefix + "%", keyPrefix + "%");
            jdbcTemplate.update("DELETE FROM ast_product_type_source_mapping WHERE tenant_id=1 AND source_key LIKE ?",
                    keyPrefix + "%");
            jdbcTemplate.update("DELETE FROM ast_product_type WHERE tenant_id=1 AND source_key LIKE ?", keyPrefix + "%");
            jdbcTemplate.update("DELETE FROM ast_device WHERE id BETWEEN ? AND ?", idBase, idBase + 99);
            jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE correlation_id LIKE ?", keyPrefix + "%");
            jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?", keyPrefix + "%");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void concurrentSameStableCodeAllowsAtMostOneFirstInsert() throws Exception {
        LocalDateTime watermark = LocalDateTime.of(2026, 8, 31, 14, 0);
        String typeCode = typeCode("CONCURRENT");
        ImportAssetProductTypeCommand left = command("same-code-left", typeCode, watermark, List.of());
        ImportAssetProductTypeCommand right = command("same-code-right", typeCode, watermark, List.of());

        List<Outcome<ImportAssetProductTypeResult>> outcomes = runConcurrently(
                () -> importWriter.importOnce(1L, 9L, left),
                () -> importWriter.importOnce(1L, 9L, right));

        assertEquals(1, outcomes.stream().filter(Outcome::successful).count(), outcomes.toString());
        assertEquals(1, outcomes.stream().filter(outcome -> !outcome.successful()).count(), outcomes.toString());
        assertTrue(outcomes.stream().filter(outcome -> !outcome.successful())
                .allMatch(outcome -> isExpectedCodeCompetition(outcome.failure())), outcomes.toString());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ast_product_type WHERE tenant_id=1 AND type_code=?",
                Long.class, typeCode));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ast_product_type_source_mapping WHERE tenant_id=1 AND source_key LIKE ?",
                Long.class, keyPrefix + "%"));
    }

    @Test
    void concurrentSameSourceDifferentTargetKeepsOneMappingAndRecordsConflict() throws Exception {
        LocalDateTime watermark = LocalDateTime.of(2026, 8, 31, 15, 0);
        String sourceKey = keyPrefix + "-same-source";
        ImportAssetProductTypeCommand left = command("source-left", typeCode("SOURCE-A"), sourceKey, watermark,
                "a".repeat(64), List.of());
        ImportAssetProductTypeCommand right = command("source-right", typeCode("SOURCE-B"), sourceKey, watermark,
                "b".repeat(64), List.of());
        importWriter.importOnce(1L, 9L, left);

        List<Outcome<ImportAssetProductTypeResult>> outcomes = runConcurrently(
                () -> importAndRecordConflict(left),
                () -> importAndRecordConflict(right));

        assertEquals(1, outcomes.stream().filter(Outcome::successful).count(), outcomes.toString());
        assertEquals(1, outcomes.stream().filter(outcome -> !outcome.successful()).count(), outcomes.toString());
        assertTrue(outcomes.stream().filter(outcome -> !outcome.successful())
                .allMatch(outcome -> outcome.failure() instanceof AssetProductTypeImportRejectedException
                        && "SOURCE_CONFLICT".equals(
                                ((AssetProductTypeImportRejectedException) outcome.failure()).rejectionCode())),
                outcomes.toString());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ast_product_type_source_mapping "
                        + "WHERE tenant_id=1 AND source_system='CRM' AND source_key=?", Long.class, sourceKey));
        assertEquals("CONFLICT", jdbcTemplate.queryForObject(
                "SELECT mapping_status FROM ast_product_type_source_mapping "
                        + "WHERE tenant_id=1 AND source_system='CRM' AND source_key=?", String.class, sourceKey));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT conflict_product_type_code FROM ast_product_type_source_mapping "
                        + "WHERE tenant_id=1 AND source_system='CRM' AND source_key=?", String.class, sourceKey));
    }

    @Test
    void concurrentDeviceUpdatesLeaveAtMostOneCurrentReference() throws Exception {
        long deviceId = idBase + 1;
        insertDevice(deviceId);
        LocalDateTime watermark = LocalDateTime.of(2026, 8, 31, 16, 0);
        ImportAssetProductTypeCommand left = command("device-left", typeCode("DEVICE-A"), watermark,
                List.of(new DeviceCurrentProductTypeInput(deviceId, "RESOLVED")));
        ImportAssetProductTypeCommand right = command("device-right", typeCode("DEVICE-B"), watermark.plusMinutes(1),
                List.of(new DeviceCurrentProductTypeInput(deviceId, "RESOLVED")));

        List<Outcome<ImportAssetProductTypeResult>> outcomes = runConcurrently(
                () -> importWriter.importOnce(1L, 9L, left),
                () -> importWriter.importOnce(1L, 9L, right));

        assertTrue(outcomes.stream().anyMatch(Outcome::successful), outcomes.toString());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ast_device_current_product_type "
                        + "WHERE tenant_id=1 AND device_id=? AND current_marker=1", Long.class, deviceId));
        long historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ast_device_current_product_type WHERE tenant_id=1 AND device_id=?",
                Long.class, deviceId);
        assertTrue(historyCount >= 1 && historyCount <= 2, String.valueOf(historyCount));
    }

    @Test
    void concurrentSameIdempotencyKeyHasOneNewDecision() throws Exception {
        ImportAssetProductTypeCommand command = command("idempotency", typeCode("IDEMPOTENCY"),
                LocalDateTime.of(2026, 8, 31, 17, 0), List.of());

        List<Outcome<PlatformCommandExecutionApi.ExecutionResult<ImportAssetProductTypeResult>>> outcomes =
                runConcurrently(() -> execute(command), () -> execute(command));

        assertEquals(2, outcomes.stream().filter(Outcome::successful).count(), outcomes.toString());
        assertEquals(1, outcomes.stream().filter(Outcome::successful)
                .map(Outcome::result)
                .filter(result -> result.decision() == PlatformCommandExecutionApi.Decision.NEW).count(),
                outcomes.toString());
        assertTrue(outcomes.stream().map(Outcome::result)
                .allMatch(result -> result.decision() == PlatformCommandExecutionApi.Decision.NEW
                        || result.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                        || result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS), outcomes.toString());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=1 AND scope_code=? "
                        + "AND actor_id=9 AND idempotency_key=?",
                Long.class, AssetProductTypeImportService.IMPORT_SCOPE, command.idempotencyKey()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ast_product_type WHERE tenant_id=1 AND source_key=?",
                Long.class, command.sourceKey()));
    }

    private ImportAssetProductTypeResult importAndRecordConflict(ImportAssetProductTypeCommand command) {
        try {
            return importWriter.importOnce(1L, 9L, command);
        } catch (AssetProductTypeImportRejectedException rejection) {
            recordConflict(rejection);
            throw rejection;
        }
    }

    private void recordConflict(AssetProductTypeImportRejectedException rejection) {
        if (rejection.conflict()) {
            conflictRecordService.record(1L, 9L, rejection);
        }
    }

    private boolean isExpectedCodeCompetition(Throwable failure) {
        return failure instanceof ConcurrencyFailureException
                || failure instanceof AssetProductTypeImportRejectedException rejection
                && "PRODUCT_TYPE_CODE_CONFLICT".equals(rejection.rejectionCode());
    }

    private PlatformCommandExecutionApi.ExecutionResult<ImportAssetProductTypeResult> execute(
            ImportAssetProductTypeCommand command) {
        return commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        1L, AssetProductTypeImportService.IMPORT_SCOPE, 9L, command.idempotencyKey()),
                command.payloadHash(), ImportAssetProductTypeResult.class,
                () -> importWriter.importOnce(1L, 9L, command), result -> new PlatformCommandExecutionApi.SuccessFacts(
                        AssetProductTypeImportService.IMPORT_OPERATION, "AssetProductType",
                        String.valueOf(result.productTypeId()), command.operationId(), "{}", null, null));
    }

    private String typeCode(String suffix) {
        return "TYPE-" + suffix + "-" + Long.toUnsignedString(idBase, 36);
    }

    private ImportAssetProductTypeCommand command(String suffix, String code, LocalDateTime sourceUpdatedAt,
                                                   List<DeviceCurrentProductTypeInput> devices) {
        return command(suffix, code, keyPrefix + "-" + suffix, sourceUpdatedAt, "c".repeat(64), devices);
    }

    private ImportAssetProductTypeCommand command(String suffix, String code, String sourceKey,
                                                   LocalDateTime sourceUpdatedAt, String payloadHash,
                                                   List<DeviceCurrentProductTypeInput> devices) {
        return new ImportAssetProductTypeCommand(keyPrefix + "-" + suffix + "-op",
                keyPrefix + "-" + suffix + "-idem", code, "并发测试类型", true,
                "CRM", sourceKey, keyPrefix + "-" + suffix + "-version", sourceUpdatedAt, payloadHash, devices);
    }

    private void insertDevice(long id) {
        jdbcTemplate.update("INSERT INTO ast_device "
                        + "(id,sn,name,project_assignment_version,status,source_system,source_key,sync_status,version,"
                        + "creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,0,'ACTIVE','PMS',?,'FRESH',0,'mysql-it','mysql-it',b'0',1)",
                id, "IT-FAST002-CONCURRENT-" + id, "FAST002", keyPrefix + "-device-" + id);
    }

    private <T> List<Outcome<T>> runConcurrently(Callable<T> left, Callable<T> right) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Outcome<T>>> futures = List.of(
                    executor.submit(awaitStart(ready, start, left)),
                    executor.submit(awaitStart(ready, start, right)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            try {
                for (Future<Outcome<T>> future : futures) {
                    outcomes.add(future.get(20, TimeUnit.SECONDS));
                }
                return outcomes;
            } catch (TimeoutException timeout) {
                futures.forEach(future -> future.cancel(true));
                throw timeout;
            }
        }
    }

    private <T> Callable<Outcome<T>> awaitStart(CountDownLatch ready, CountDownLatch start, Callable<T> operation) {
        return () -> {
            TenantContextHolder.setTenantId(1L);
            try {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发启动超时");
                }
                return new Outcome<>(operation.call(), null);
            } catch (Throwable failure) {
                return new Outcome<>(null, failure);
            } finally {
                TenantContextHolder.clear();
            }
        };
    }

    private record Outcome<T>(T result, Throwable failure) {
        boolean successful() {
            return failure == null;
        }
    }
}
