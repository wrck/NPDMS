package cn.iocoder.yudao.module.pms.platform.file;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileLifecycleApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ArchiveFileReferenceCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ChangeFileAvailabilityCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.DeleteDraftFileCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.DetachFileReferenceCommand;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = FileLifecycleMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FileLifecycleMySqlIntegrationTest {

    @Resource FileLifecycleApplicationService service;
    @Resource FileBusinessObjectPolicyRegistry policyRegistry;
    @Resource SecurityFrameworkService securityFrameworkService;
    @Resource FileArtifactMapper artifactMapper;
    @Resource FileVersionMapper versionMapper;
    @Resource FileReferenceMapper referenceMapper;
    @Resource FileUploadSessionMapper uploadSessionMapper;
    @Resource JdbcTemplate jdbcTemplate;

    private Long artifactId;
    private Long referenceId;
    private Long draftArtifactId;
    private Long mismatchArtifactId;

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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.platform");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE tenant_id=0 "
                + "AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.operationId')) LIKE 'life-%'");
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=0 AND correlation_id LIKE 'life-%'");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=0 AND idempotency_key LIKE 'life-%'");
        reset(policyRegistry, securityFrameworkService);
        when(policyRegistry.inspect(any())).thenReturn(policy());
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(policy());
        when(securityFrameworkService.hasPermission(any())).thenReturn(true);
        createActiveFacts();
        createDraft();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE tenant_id=0 AND aggregate_key IN (?, ?)",
                String.valueOf(artifactId), String.valueOf(referenceId));
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=0 AND aggregate_key IN (?, ?, ?, ?)",
                String.valueOf(artifactId), String.valueOf(referenceId),
                String.valueOf(draftArtifactId), String.valueOf(mismatchArtifactId));
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=0 AND idempotency_key LIKE 'life-%'");
        jdbcTemplate.update("DELETE FROM plt_file_archive_record WHERE tenant_id=0 AND artifact_id=?", artifactId);
        jdbcTemplate.update("DELETE FROM plt_file_upload_session WHERE tenant_id=0 AND artifact_id IN (?, ?, ?)",
                artifactId, draftArtifactId, mismatchArtifactId);
        jdbcTemplate.update("DELETE FROM plt_file_reference WHERE tenant_id=0 AND artifact_id IN (?, ?, ?)",
                artifactId, draftArtifactId, mismatchArtifactId);
        jdbcTemplate.update("DELETE FROM plt_file_version WHERE tenant_id=0 AND artifact_id IN (?, ?, ?)",
                artifactId, draftArtifactId, mismatchArtifactId);
        jdbcTemplate.update("DELETE FROM plt_file_artifact WHERE tenant_id=0 AND id IN (?, ?, ?)",
                artifactId, draftArtifactId, mismatchArtifactId);
    }

    @Test
    void detachesAndReplaysWithoutDuplicateEvent() {
        var command = new DetachFileReferenceCommand(0L, 9L, "life-detach", referenceId, 0,
                "SOL", "CONSTRUCTION_PLAN_CHANGE", "99001", "CUSTOMER_DELAY_EVIDENCE", "slot-a", "误传");

        var first = service.detach(command);
        var replay = service.detach(command);

        assertEquals("DETACHED", first.status());
        assertEquals(first, replay);
        assertEquals("DETACHED", referenceStatus());
        assertEquals(1L, outboxCount("FileReferenceDetached"));
        assertEquals(1L, auditCount("FILE_REFERENCE_DETACH", "SUCCESS"));
    }

    @Test
    void rejectsDifferentPayloadForTheSameIdempotencyKey() {
        var command = new DetachFileReferenceCommand(0L, 9L, "life-detach-conflict", referenceId, 0,
                "SOL", "CONSTRUCTION_PLAN_CHANGE", "99001", "CUSTOMER_DELAY_EVIDENCE", "slot-a", "误传");
        service.detach(command);

        var changed = new DetachFileReferenceCommand(0L, 9L, "life-detach-conflict", referenceId, 0,
                "SOL", "CONSTRUCTION_PLAN_CHANGE", "99001", "CUSTOMER_DELAY_EVIDENCE", "slot-a", "重复材料");
        assertThrows(RuntimeException.class, () -> service.detach(changed));

        assertEquals("DETACHED", referenceStatus());
        assertEquals(1L, outboxCount("FileReferenceDetached"));
        assertEquals(1L, auditCount("FILE_REFERENCE_DETACH", "SUCCESS"));
    }

    @Test
    void rejectsDetachWhenBusinessReferenceIsImmutable() {
        var immutable = new FileBusinessObjectPolicyFact(true, 8L, "IMMUTABLE", "MULTIPLE",
                Set.of("CUSTOMER_EVIDENCE"), Set.of("application/pdf"), 52_428_800L, "INTERNAL");
        when(policyRegistry.inspect(any())).thenReturn(immutable);
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(immutable);

        var command = new DetachFileReferenceCommand(0L, 9L, "life-detach-immutable", referenceId, 0,
                "SOL", "CONSTRUCTION_PLAN_CHANGE", "99001", "CUSTOMER_DELAY_EVIDENCE", "slot-a", "误传");
        assertThrows(RuntimeException.class, () -> service.detach(command));

        assertEquals("ACTIVE", referenceStatus());
        assertEquals(0L, outboxCount("FileReferenceDetached"));
        assertEquals(1L, auditCount("FILE_REFERENCE_DETACH", "REJECTED"));
    }

    @Test
    void invalidatesThenRestoresWithoutChangingContentDigest() {
        String before = jdbcTemplate.queryForObject(
                "SELECT sha256 FROM plt_file_version WHERE tenant_id=0 AND artifact_id=? AND version_no=1",
                String.class, artifactId);

        service.changeAvailability(availability("life-unavailable", 0, "UNAVAILABLE", "CONTENT_MISSING"));
        service.changeAvailability(availability("life-unavailable-restore", 1, "AVAILABLE", null));
        service.changeAvailability(availability("life-invalid", 2, "INVALIDATED", "COMPLIANCE"));
        service.changeAvailability(availability("life-invalid-restore", 3, "AVAILABLE", null));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT sha256, availability_status_code, availability_version FROM plt_file_version "
                        + "WHERE tenant_id=0 AND artifact_id=? AND version_no=1", artifactId);
        assertEquals(before, row.get("sha256"));
        assertEquals("AVAILABLE", row.get("availability_status_code"));
        assertEquals(4, ((Number) row.get("availability_version")).intValue());
    }

    @Test
    void rejectsAvailabilityChangeWhenAuthorizedKeyBelongsToAnotherArtifact() {
        mismatchArtifactId = createUnboundActiveArtifact();
        var command = new ChangeFileAvailabilityCommand(0L, 9L, "life-mismatch-availability",
                mismatchArtifactId, 1, 0, "INVALIDATED", "COMPLIANCE", "错配",
                "SOL", "CONSTRUCTION_PLAN_CHANGE", "99001", "CUSTOMER_DELAY_EVIDENCE", "slot-a");

        assertThrows(RuntimeException.class, () -> service.changeAvailability(command));

        assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
                "SELECT availability_status_code FROM plt_file_version WHERE tenant_id=0 AND artifact_id=? "
                        + "AND version_no=1", String.class, mismatchArtifactId));
        assertEquals(0L, auditCount("FILE_VERSION_AVAILABILITY_CHANGE", "SUCCESS"));
        assertEquals(0L, completedIdempotencyCount("life-mismatch-availability"));
        assertEquals(0L, outboxCount("FileArchived"));
    }

    @Test
    void archivesOnceAcrossConcurrentDifferentIdempotencyKeys() throws Exception {
        var commandA = new ArchiveFileReferenceCommand(0L, 9L, "life-archive-a", referenceId, 0,
                "batch-01", "decision-01", "归档", "SOL", "CONSTRUCTION_PLAN_CHANGE",
                "99001", "CUSTOMER_DELAY_EVIDENCE", "slot-a");
        var commandB = new ArchiveFileReferenceCommand(0L, 9L, "life-archive-b", referenceId, 0,
                "batch-01", "decision-01", "归档", "SOL", "CONSTRUCTION_PLAN_CHANGE",
                "99001", "CUSTOMER_DELAY_EVIDENCE", "slot-a");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<FileLifecycleApplicationService.LifecycleResult> first = executor.submit(() -> {
                ready.countDown(); start.await(); return service.archive(commandA);
            });
            Future<FileLifecycleApplicationService.LifecycleResult> second = executor.submit(() -> {
                ready.countDown(); start.await(); return service.archive(commandB);
            });
            ready.await();
            start.countDown();
            assertEquals("ARCHIVED", first.get().status());
            assertEquals("ARCHIVED", second.get().status());
        }

        assertEquals(1L, count("plt_file_archive_record", "artifact_id", artifactId));
        assertEquals(1L, outboxCount("FileArchived"));
        assertEquals(2L, auditCount("FILE_REFERENCE_ARCHIVE", "SUCCESS"));

        var conflict = new ArchiveFileReferenceCommand(0L, 9L, "life-archive-conflict", referenceId, 0,
                "batch-01", "decision-02", "归档", "SOL", "CONSTRUCTION_PLAN_CHANGE",
                "99001", "CUSTOMER_DELAY_EVIDENCE", "slot-a");
        assertThrows(RuntimeException.class, () -> service.archive(conflict));
        assertEquals(1L, count("plt_file_archive_record", "artifact_id", artifactId));
        assertEquals(1L, outboxCount("FileArchived"));
    }

    @Test
    void logicallyDeletesOnlyUnreferencedDraft() {
        var result = service.deleteDraft(new DeleteDraftFileCommand(0L, 9L, "life-delete",
                draftArtifactId, 0, "SOL", "CONSTRUCTION_PLAN_CHANGE", "99002",
                "CUSTOMER_DELAY_EVIDENCE", "draft-slot", "取消草稿"));

        assertEquals("DELETED", result.status());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM plt_file_artifact WHERE tenant_id=0 AND id=?", Integer.class, draftArtifactId));
    }

    @Test
    void rejectsDraftDeleteWhenAnyReferenceExists() {
        createDraftReference();
        var command = new DeleteDraftFileCommand(0L, 9L, "life-delete-referenced",
                draftArtifactId, 0, "SOL", "CONSTRUCTION_PLAN_CHANGE", "99002",
                "CUSTOMER_DELAY_EVIDENCE", "draft-slot", "取消草稿");

        assertThrows(RuntimeException.class, () -> service.deleteDraft(command));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT deleted FROM plt_file_artifact WHERE tenant_id=0 AND id=?", Integer.class, draftArtifactId));
        assertEquals(1L, count("plt_file_reference", "artifact_id", draftArtifactId));
        assertEquals(1L, auditCount("FILE_DRAFT_DELETE", "REJECTED"));
    }

    @Test
    void rejectsDraftDeleteWhenAuthorizedKeyDoesNotOwnArtifact() {
        var command = new DeleteDraftFileCommand(0L, 9L, "life-delete-mismatch",
                draftArtifactId, 0, "SOL", "CONSTRUCTION_PLAN_CHANGE", "99001",
                "CUSTOMER_DELAY_EVIDENCE", "slot-a", "取消草稿");

        assertThrows(RuntimeException.class, () -> service.deleteDraft(command));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT deleted FROM plt_file_artifact WHERE tenant_id=0 AND id=?", Integer.class, draftArtifactId));
        assertEquals(0L, auditCount("FILE_DRAFT_DELETE", "SUCCESS"));
        assertEquals(0L, completedIdempotencyCount("life-delete-mismatch"));
        assertEquals(0L, outboxCount("FileArchived"));
    }

    private ChangeFileAvailabilityCommand availability(String key, int expected, String target, String reason) {
        return new ChangeFileAvailabilityCommand(0L, 9L, key, artifactId, 1, expected, target,
                reason, reason, "SOL", "CONSTRUCTION_PLAN_CHANGE", "99001",
                "CUSTOMER_DELAY_EVIDENCE", "slot-a");
    }

    private void createActiveFacts() {
        LocalDateTime now = LocalDateTime.now();
        FileArtifactDO artifact = artifact("ACTIVE", "active.pdf", now);
        assertEquals(1, artifactMapper.insert(artifact));
        artifactId = artifact.getId();
        FileVersionDO version = new FileVersionDO();
        version.setTenantId(0L); version.setArtifactId(artifactId); version.setVersionNo(1);
        version.setInfraFileId(8_910_001L); version.setAvailabilityVersion(0);
        version.setSha256("a".repeat(64)); version.setSizeBytes(128L);
        version.setDeclaredMediaType("application/pdf"); version.setDetectedMediaType("application/pdf");
        version.setScanStatusCode("PASSED"); version.setAvailabilityStatusCode("AVAILABLE");
        version.setCreatedBy(9L); version.setCreatedAt(now);
        assertEquals(1, versionMapper.insert(version));
        FileReferenceDO reference = new FileReferenceDO();
        reference.setTenantId(0L); reference.setOwnerContext("SOL");
        reference.setObjectType("CONSTRUCTION_PLAN_CHANGE"); reference.setObjectId("99001");
        reference.setPurposeCode("CUSTOMER_DELAY_EVIDENCE"); reference.setReferenceKey("slot-a");
        reference.setArtifactId(artifactId); reference.setFileVersionNo(1);
        reference.setSensitivityCode("INTERNAL"); reference.setStatusCode("ACTIVE");
        reference.setScopeVersion(8L); reference.setVersion(0); reference.setCreator("9");
        reference.setUpdater("9"); reference.setCreateTime(now); reference.setUpdateTime(now);
        assertEquals(1, referenceMapper.insert(reference));
        referenceId = reference.getId();
    }

    private void createDraft() {
        FileArtifactDO draft = artifact("DRAFT", "draft.pdf", LocalDateTime.now());
        assertEquals(1, artifactMapper.insert(draft));
        draftArtifactId = draft.getId();
        createDraftBinding();
    }

    private void createDraftBinding() {
        LocalDateTime now = LocalDateTime.now();
        FileUploadSessionDO session = new FileUploadSessionDO();
        session.setModeCode("CREATE_ARTIFACT"); session.setOwnerContext("SOL");
        session.setObjectType("CONSTRUCTION_PLAN_CHANGE"); session.setObjectId("99002");
        session.setPurposeCode("CUSTOMER_DELAY_EVIDENCE"); session.setReferenceKey("draft-slot");
        session.setFileName("draft.pdf"); session.setCategoryCode("CUSTOMER_EVIDENCE");
        session.setDeclaredSizeBytes(64L); session.setDeclaredMediaType("application/pdf");
        session.setStorageOperationId("life-draft-" + draftArtifactId); session.setStatusCode("FAILED_FINAL");
        session.setScopeVersion(8L); session.setExpiresAt(now.plusMinutes(15)); session.setVersion(1);
        session.setArtifactId(draftArtifactId); session.setFailureCode("CANCELLED");
        session.setCreator("9"); session.setUpdater("9"); session.setCreateTime(now); session.setUpdateTime(now);
        session.setTenantId(0L);
        assertEquals(1, uploadSessionMapper.insert(session));
    }

    private Long createUnboundActiveArtifact() {
        LocalDateTime now = LocalDateTime.now();
        FileArtifactDO artifact = artifact("ACTIVE", "other.pdf", now);
        assertEquals(1, artifactMapper.insert(artifact));
        FileVersionDO version = new FileVersionDO();
        version.setTenantId(0L); version.setArtifactId(artifact.getId()); version.setVersionNo(1);
        version.setInfraFileId(8_910_003L); version.setAvailabilityVersion(0);
        version.setSha256("c".repeat(64)); version.setSizeBytes(32L);
        version.setDeclaredMediaType("application/pdf"); version.setDetectedMediaType("application/pdf");
        version.setScanStatusCode("PASSED"); version.setAvailabilityStatusCode("AVAILABLE");
        version.setCreatedBy(9L); version.setCreatedAt(now);
        assertEquals(1, versionMapper.insert(version));
        return artifact.getId();
    }

    private void createDraftReference() {
        LocalDateTime now = LocalDateTime.now();
        FileVersionDO version = new FileVersionDO();
        version.setTenantId(0L); version.setArtifactId(draftArtifactId); version.setVersionNo(1);
        version.setInfraFileId(8_910_002L); version.setAvailabilityVersion(0);
        version.setSha256("b".repeat(64)); version.setSizeBytes(64L);
        version.setDeclaredMediaType("application/pdf"); version.setDetectedMediaType("application/pdf");
        version.setScanStatusCode("PASSED"); version.setAvailabilityStatusCode("AVAILABLE");
        version.setCreatedBy(9L); version.setCreatedAt(now);
        assertEquals(1, versionMapper.insert(version));
        FileReferenceDO reference = new FileReferenceDO();
        reference.setTenantId(0L); reference.setOwnerContext("SOL");
        reference.setObjectType("CONSTRUCTION_PLAN_CHANGE"); reference.setObjectId("99002");
        reference.setPurposeCode("CUSTOMER_DELAY_EVIDENCE"); reference.setReferenceKey("draft-slot");
        reference.setArtifactId(draftArtifactId); reference.setFileVersionNo(1);
        reference.setSensitivityCode("INTERNAL"); reference.setStatusCode("ACTIVE");
        reference.setScopeVersion(8L); reference.setVersion(0); reference.setCreator("9");
        reference.setUpdater("9"); reference.setCreateTime(now); reference.setUpdateTime(now);
        assertEquals(1, referenceMapper.insert(reference));
    }

    private FileArtifactDO artifact(String status, String name, LocalDateTime now) {
        FileArtifactDO artifact = new FileArtifactDO();
        artifact.setTenantId(0L); artifact.setName(name); artifact.setCategoryCode("CUSTOMER_EVIDENCE");
        artifact.setOwnerContext("SOL"); artifact.setLifecycleStatusCode(status); artifact.setVersion(0);
        artifact.setCreator("9"); artifact.setUpdater("9"); artifact.setCreateTime(now); artifact.setUpdateTime(now);
        return artifact;
    }

    private String referenceStatus() {
        return jdbcTemplate.queryForObject("SELECT status_code FROM plt_file_reference WHERE tenant_id=0 AND id=?",
                String.class, referenceId);
    }
    private long outboxCount(String type) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=0 "
                + "AND event_type=?", Long.class, type);
    }
    private long auditCount(String operation, String result) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=0 "
                + "AND operation_code=? AND result_code=?", Long.class, operation, result);
    }
    private long completedIdempotencyCount(String key) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=0 "
                + "AND idempotency_key=? AND status='COMPLETED'", Long.class, key);
    }
    private long count(String table, String column, Object value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=0 AND "
                + column + "=?", Long.class, value);
    }
    private FileBusinessObjectPolicyFact policy() {
        return new FileBusinessObjectPolicyFact(true, 8L, "MUTABLE", "MULTIPLE",
                Set.of("CUSTOMER_EVIDENCE"), Set.of("application/pdf"), 52_428_800L, "INTERNAL");
    }

    private static Map<String, String> environment() {
        Map<String, String> environment = new LinkedHashMap<>(System.getenv());
        Path dotenv = findRepositoryDotenv();
        if (dotenv == null) return environment;
        try {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) continue;
                int separator = value.indexOf('=');
                if (separator > 0) environment.putIfAbsent(value.substring(0, separator).trim(),
                        unquote(value.substring(separator + 1).trim()));
            }
            return environment;
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
                || (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1);
        return value;
    }
    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + key);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.platform.dal.mysql.file",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class,
            cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter.class,
            OperationAuditApiImpl.class,
            FileLifecycleApplicationService.class, FileEventFactory.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean FileBusinessObjectPolicyRegistry policyRegistry() { return mock(FileBusinessObjectPolicyRegistry.class); }
        @Bean SecurityFrameworkService securityFrameworkService() { return mock(SecurityFrameworkService.class); }
    }
}
