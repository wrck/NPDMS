package cn.iocoder.yudao.module.pms.platform.file;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.ExactFileReferenceQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactActivationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceCursorQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceReplaceVersionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionCompletionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionCursorQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = FileMapperMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FileMapperMySqlIntegrationTest {

    private static final long TENANT_ID = 0L;

    @Resource FileArtifactMapper artifactMapper;
    @Resource FileVersionMapper versionMapper;
    @Resource FileReferenceMapper referenceMapper;
    @Resource FileUploadSessionMapper uploadSessionMapper;
    @Resource JdbcTemplate jdbcTemplate;

    private String objectId;
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
        TenantContextHolder.setTenantId(TENANT_ID);
        objectId = "it-fplt001-task3-" + UUID.randomUUID();
        artifactId = insertArtifact("DRAFT");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM plt_file_upload_session WHERE tenant_id = ? AND object_id = ?",
                TENANT_ID, objectId);
        jdbcTemplate.update("DELETE FROM plt_file_reference WHERE tenant_id = ? AND object_id = ?",
                TENANT_ID, objectId);
        jdbcTemplate.update("DELETE FROM plt_file_version WHERE tenant_id = ? AND artifact_id = ?",
                TENANT_ID, artifactId);
        jdbcTemplate.update("DELETE FROM plt_file_artifact WHERE tenant_id = ? AND id = ?",
                TENANT_ID, artifactId);
        TenantContextHolder.clear();
    }

    @Test
    void selectsOnlyTheExactReferenceSlotAndUsesStableCursors() {
        insertVersion(1, 8_100_001L);
        insertVersion(2, 8_100_002L);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long firstId = insertReference("slot-a", 1, now.minusSeconds(1));
        Long secondId = insertReference("slot-b", 2, now);

        FileReferenceDO exact = referenceMapper.selectExact(exactReference("slot-a"));
        assertNotNull(exact);
        assertEquals(firstId, exact.getId());
        assertNull(referenceMapper.selectExact(exactReference("missing")));
        assertNull(referenceMapper.selectExact(new ExactFileReferenceQuery(
                1L, "SOL", "DURATION_CHANGE", objectId, "CUSTOMER_EVIDENCE", "slot-a")));

        var firstPage = referenceMapper.selectCursor(new FileReferenceCursorQuery(
                TENANT_ID, "SOL", "DURATION_CHANGE", objectId, null, null, 1));
        assertEquals(secondId, firstPage.getFirst().getId());
        var nextPage = referenceMapper.selectCursor(new FileReferenceCursorQuery(
                TENANT_ID, "SOL", "DURATION_CHANGE", objectId,
                firstPage.getFirst().getCreateTime(), firstPage.getFirst().getId(), 1));
        assertEquals(firstId, nextPage.getFirst().getId());

        var versions = versionMapper.selectCursor(new FileVersionCursorQuery(
                TENANT_ID, artifactId, null, null, 1));
        assertEquals(2, versions.getFirst().getVersionNo());
        assertEquals(1, versionMapper.selectCursor(new FileVersionCursorQuery(
                TENANT_ID, artifactId, versions.getFirst().getVersionNo(), versions.getFirst().getId(), 1))
                .getFirst().getVersionNo());
    }

    @Test
    void locksTenantFactsAndAppliesOnlyMatchingCasUpdates() {
        insertVersion(1, 8_200_001L);
        insertVersion(2, 8_200_002L);
        Long referenceId = insertReference("slot-a", 1, LocalDateTime.now());
        Long sessionId = insertValidatingSession();

        assertNotNull(artifactMapper.selectForUpdate(new FileArtifactLockQuery(TENANT_ID, artifactId)));
        assertNull(artifactMapper.selectForUpdate(new FileArtifactLockQuery(1L, artifactId)));
        assertNotNull(versionMapper.selectForUpdate(new FileVersionLockQuery(TENANT_ID, artifactId, 1)));
        assertNotNull(referenceMapper.selectForUpdate(new FileReferenceLockQuery(
                TENANT_ID, "SOL", "DURATION_CHANGE", objectId, "CUSTOMER_EVIDENCE", "slot-a")));
        FileReferenceSetQuery setQuery = new FileReferenceSetQuery(TENANT_ID, "SOL", "DURATION_CHANGE",
                objectId, "CUSTOMER_EVIDENCE");
        assertEquals(List.of("slot-a"), referenceMapper.selectActiveSet(setQuery).stream()
                .map(FileReferenceDO::getReferenceKey).toList());
        assertEquals(List.of("slot-a"), referenceMapper.selectSetForUpdate(setQuery).stream()
                .map(FileReferenceDO::getReferenceKey).toList());
        assertTrue(referenceMapper.selectSetForUpdate(new FileReferenceSetQuery(TENANT_ID, "SOL",
                "DURATION_CHANGE", objectId, "EMPTY_PURPOSE")).isEmpty());
        assertNotNull(uploadSessionMapper.selectForUpdate(new FileUploadSessionLockQuery(TENANT_ID, sessionId)));

        assertEquals(1, artifactMapper.activateDraftIfMatch(
                new FileArtifactActivationUpdate(TENANT_ID, artifactId, 0)));
        assertEquals(0, artifactMapper.activateDraftIfMatch(
                new FileArtifactActivationUpdate(TENANT_ID, artifactId, 0)));

        assertEquals(1, referenceMapper.replaceVersionIfMatch(new FileReferenceReplaceVersionUpdate(
                TENANT_ID, referenceId, 0, artifactId, 2, 8L, "INTERNAL")));
        assertEquals(0, referenceMapper.replaceVersionIfMatch(new FileReferenceReplaceVersionUpdate(
                TENANT_ID, referenceId, 0, artifactId, 1, 8L, "INTERNAL")));
        FileReferenceDO replaced = referenceMapper.selectExact(exactReference("slot-a"));
        assertEquals(artifactId, replaced.getArtifactId());
        assertEquals(2, replaced.getFileVersionNo());
        assertEquals(1, replaced.getVersion());

        LocalDateTime completedAt = LocalDateTime.now();
        assertEquals(1, uploadSessionMapper.completeIfValidating(new FileUploadSessionCompletionUpdate(
                TENANT_ID, sessionId, 0, artifactId, referenceId, "b".repeat(64),
                2, 8_200_002L, completedAt)));
        assertEquals(0, uploadSessionMapper.completeIfValidating(new FileUploadSessionCompletionUpdate(
                TENANT_ID, sessionId, 0, artifactId, referenceId, "b".repeat(64),
                2, 8_200_002L, completedAt)));
        FileUploadSessionDO completed = uploadSessionMapper.selectForUpdate(
                new FileUploadSessionLockQuery(TENANT_ID, sessionId));
        assertEquals("COMPLETED", completed.getStatusCode());
        assertEquals(1, completed.getVersion());
    }

    private Long insertArtifact(String status) {
        FileArtifactDO row = new FileArtifactDO();
        row.setName("task3.pdf");
        row.setCategoryCode("CUSTOMER_EVIDENCE");
        row.setOwnerContext("SOL");
        row.setLifecycleStatusCode(status);
        row.setVersion(0);
        row.setCreator("it-fplt001-task3");
        row.setUpdater("it-fplt001-task3");
        row.setTenantId(TENANT_ID);
        assertEquals(1, artifactMapper.insert(row));
        return row.getId();
    }

    private void insertVersion(int versionNo, long infraFileId) {
        FileVersionDO row = new FileVersionDO();
        row.setArtifactId(artifactId);
        row.setVersionNo(versionNo);
        row.setInfraFileId(infraFileId + Math.abs(objectId.hashCode()));
        row.setAvailabilityVersion(0);
        row.setSha256(Integer.toHexString(versionNo).repeat(64).substring(0, 64));
        row.setSizeBytes(12L);
        row.setDeclaredMediaType("application/pdf");
        row.setDetectedMediaType("application/pdf");
        row.setScanStatusCode("PASSED");
        row.setAvailabilityStatusCode("AVAILABLE");
        row.setCreatedBy(9L);
        row.setCreatedAt(LocalDateTime.now().plusSeconds(versionNo));
        row.setTenantId(TENANT_ID);
        assertEquals(1, versionMapper.insert(row));
    }

    private Long insertReference(String referenceKey, int fileVersionNo, LocalDateTime createdAt) {
        FileReferenceDO row = new FileReferenceDO();
        row.setOwnerContext("SOL");
        row.setObjectType("DURATION_CHANGE");
        row.setObjectId(objectId);
        row.setPurposeCode("CUSTOMER_EVIDENCE");
        row.setReferenceKey(referenceKey);
        row.setArtifactId(artifactId);
        row.setFileVersionNo(fileVersionNo);
        row.setSensitivityCode("INTERNAL");
        row.setStatusCode("ACTIVE");
        row.setScopeVersion(7L);
        row.setVersion(0);
        row.setCreator("it-fplt001-task3");
        row.setCreateTime(createdAt);
        row.setUpdater("it-fplt001-task3");
        row.setUpdateTime(createdAt);
        row.setTenantId(TENANT_ID);
        assertEquals(1, referenceMapper.insert(row));
        return row.getId();
    }

    private Long insertValidatingSession() {
        FileUploadSessionDO row = new FileUploadSessionDO();
        row.setModeCode("ADD_VERSION");
        row.setOwnerContext("SOL");
        row.setObjectType("DURATION_CHANGE");
        row.setObjectId(objectId);
        row.setPurposeCode("CUSTOMER_EVIDENCE");
        row.setReferenceKey("slot-a");
        row.setFileName("task3.pdf");
        row.setCategoryCode("CUSTOMER_EVIDENCE");
        row.setDeclaredSizeBytes(12L);
        row.setDeclaredMediaType("application/pdf");
        row.setStorageOperationId("task3-" + UUID.randomUUID());
        row.setStatusCode("VALIDATING");
        row.setScopeVersion(7L);
        row.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        row.setVersion(0);
        row.setArtifactId(artifactId);
        row.setExpectedReferenceVersion(0);
        row.setCreator("it-fplt001-task3");
        row.setUpdater("it-fplt001-task3");
        row.setTenantId(TENANT_ID);
        assertEquals(1, uploadSessionMapper.insert(row));
        return row.getId();
    }

    private ExactFileReferenceQuery exactReference(String referenceKey) {
        return new ExactFileReferenceQuery(TENANT_ID, "SOL", "DURATION_CHANGE", objectId,
                "CUSTOMER_EVIDENCE", referenceKey);
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
    @MapperScan("cn.iocoder.yudao.module.pms.platform.dal.mysql.file")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
