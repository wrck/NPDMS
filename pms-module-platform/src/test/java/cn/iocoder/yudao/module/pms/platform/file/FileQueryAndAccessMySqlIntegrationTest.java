package cn.iocoder.yudao.module.pms.platform.file;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageAccessReceipt;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileAccessTicketService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileArtifactApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileQueryService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = FileQueryAndAccessMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FileQueryAndAccessMySqlIntegrationTest {

    @Resource FileQueryService queryService;
    @Resource FileAccessTicketService accessService;
    @Resource FileBusinessObjectPolicyRegistry policyRegistry;
    @Resource FileStorageReceiptApi storageReceiptApi;
    @Resource SecurityFrameworkService securityFrameworkService;
    @Resource FileArtifactMapper artifactMapper;
    @Resource FileVersionMapper versionMapper;
    @Resource FileReferenceMapper referenceMapper;
    @Resource JdbcTemplate jdbcTemplate;

    private Long artifactId;
    private Long secondReferenceId;

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
        registry.add("pms.file.access-ticket-ttl", () -> "PT2M");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        LoginUser user = new LoginUser();
        user.setId(9L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        reset(policyRegistry, storageReceiptApi, securityFrameworkService);
        when(policyRegistry.inspect(any())).thenReturn(policy());
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(policy());
        when(securityFrameworkService.hasPermission(any())).thenReturn(true);
        when(storageReceiptApi.presignGet(any(), any())).thenReturn(
                new FileStorageAccessReceipt("https://private.example/signed?signature=secret",
                        LocalDateTime.now().plusMinutes(2)));
        createFacts();
    }

    @AfterEach
    void tearDown() {
        if (artifactId != null) {
            jdbcTemplate.update("DELETE FROM plt_file_access_grant WHERE tenant_id=0 AND artifact_id=?", artifactId);
            jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=0 AND aggregate_key=?",
                    String.valueOf(artifactId));
            jdbcTemplate.update("DELETE FROM plt_file_reference WHERE tenant_id=0 AND artifact_id=?", artifactId);
            jdbcTemplate.update("DELETE FROM plt_file_version WHERE tenant_id=0 AND artifact_id=?", artifactId);
            jdbcTemplate.update("DELETE FROM plt_file_artifact WHERE tenant_id=0 AND id=?", artifactId);
        }
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void queriesExactSlotAndStableVersionPagesWithoutInfraLocation() {
        FileQueryService.Actor actor = new FileQueryService.Actor(0L, 9L);
        FileQueryService.ArtifactQuery slotA = query("slot-a");
        FileQueryService.ArtifactQuery slotB = query("slot-b");

        var artifact = queryService.getArtifact(slotA, actor);
        var firstPage = queryService.getVersions(slotA, null, 1, actor);
        var secondPage = queryService.getVersions(slotA, firstPage.getNextCursor(), 1, actor);
        var secondReference = queryService.getReference(slotB, actor);

        assertEquals(artifactId, artifact.getArtifactId());
        assertEquals("slot-a", artifact.getReference().getReferenceKey());
        assertEquals(2, firstPage.getItems().getFirst().getVersionNo());
        assertTrue(firstPage.getHasMore());
        assertEquals(1, secondPage.getItems().getFirst().getVersionNo());
        assertFalse(secondPage.getHasMore());
        assertEquals(secondReferenceId, secondReference.getReferenceId());
        assertEquals("slot-b", secondReference.getReferenceKey());
        assertFalse(firstPage.getItems().getFirst().toString().contains("infraFileId"));
    }

    @Test
    void createsShortLivedGrantAndAuditsWithoutUrlOrPlainToken() {
        var response = accessService.create(accessCommand(" DOWNLOAD "));

        assertNotNull(response.getGrantId());
        assertTrue(response.getShortLivedUrl().startsWith("https://private.example/signed"));
        Map<String, Object> grant = jdbcTemplate.queryForMap(
                "SELECT token_digest, business_scope_hash, operation_code, status_code FROM plt_file_access_grant "
                        + "WHERE tenant_id=0 AND id=?", response.getGrantId());
        assertEquals(64, String.valueOf(grant.get("token_digest")).length());
        assertEquals(64, String.valueOf(grant.get("business_scope_hash")).length());
        assertEquals("DOWNLOAD", grant.get("operation_code"));
        assertEquals("ACTIVE", grant.get("status_code"));
        String audit = jdbcTemplate.queryForObject(
                "SELECT detail_snapshot FROM plt_operation_audit WHERE tenant_id=0 AND aggregate_key=? "
                        + "AND operation_code='FILE_ACCESS_TICKET_CREATE' AND result_code='SUCCESS'",
                String.class, String.valueOf(artifactId));
        assertNotNull(audit);
        assertFalse(audit.contains("private.example"));
        assertFalse(audit.contains("signature=secret"));
        assertFalse(audit.contains(String.valueOf(grant.get("token_digest"))));
    }

    @Test
    void rejectsUnavailableVersionWithoutSuccessfulGrant() {
        jdbcTemplate.update("UPDATE plt_file_version SET availability_status_code='UNAVAILABLE', "
                + "availability_version=availability_version+1 WHERE tenant_id=0 AND artifact_id=? "
                + "AND version_no=2", artifactId);

        assertThrows(RuntimeException.class, () -> accessService.create(accessCommand("DOWNLOAD")));

        assertEquals(0L, count("plt_file_access_grant", "artifact_id", artifactId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=0 AND aggregate_key=? "
                        + "AND operation_code='FILE_ACCESS_TICKET_CREATE' AND result_code='REJECTED'",
                Long.class, String.valueOf(artifactId)));
    }

    @Test
    void returnsEmptyQueriesWhenBusinessScopeIsDenied() {
        doThrow(exception(FILE_SCOPE_FORBIDDEN)).when(policyRegistry).inspect(any());
        FileQueryService.Actor actor = new FileQueryService.Actor(0L, 9L);

        assertEquals(null, queryService.getArtifact(query("slot-a"), actor));
        assertEquals(null, queryService.getReference(query("slot-a"), actor));
        var versions = queryService.getVersions(query("slot-a"), null, 20, actor);
        assertTrue(versions.getItems().isEmpty());
        assertFalse(versions.getHasMore());
    }

    @Test
    void rejectsAccessWhenFunctionalPermissionChanges() {
        when(securityFrameworkService.hasPermission("pms:file:download")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> accessService.create(accessCommand("DOWNLOAD")));

        assertEquals(0L, count("plt_file_access_grant", "artifact_id", artifactId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=0 AND aggregate_key=? "
                        + "AND operation_code='FILE_ACCESS_TICKET_CREATE' AND result_code='REJECTED'",
                Long.class, String.valueOf(artifactId)));
    }

    @Test
    void returnsNoCrossTenantMetadata() {
        TenantContextHolder.setTenantId(1L);
        try {
            FileQueryService.Actor actor = new FileQueryService.Actor(1L, 9L);
            assertThrows(RuntimeException.class, () -> queryService.getArtifact(query("slot-a"), actor));
            assertThrows(RuntimeException.class, () -> queryService.getReference(query("slot-a"), actor));
        } finally {
            TenantContextHolder.setTenantId(0L);
        }
    }

    private void createFacts() {
        LocalDateTime now = LocalDateTime.now();
        FileArtifactDO artifact = new FileArtifactDO();
        artifact.setName("evidence.pdf");
        artifact.setCategoryCode("CUSTOMER_EVIDENCE");
        artifact.setOwnerContext("SOL");
        artifact.setLifecycleStatusCode("ACTIVE");
        artifact.setVersion(1);
        artifact.setCreator("9");
        artifact.setUpdater("9");
        artifact.setCreateTime(now);
        artifact.setUpdateTime(now);
        artifact.setTenantId(0L);
        assertEquals(1, artifactMapper.insert(artifact));
        artifactId = artifact.getId();
        insertVersion(1, 8_900_101L, now.minusMinutes(1));
        insertVersion(2, 8_900_102L, now);
        insertReference("slot-a", 2, now);
        secondReferenceId = insertReference("slot-b", 1, now.minusSeconds(1));
    }

    private void insertVersion(int versionNo, long infraFileId, LocalDateTime createdAt) {
        FileVersionDO version = new FileVersionDO();
        version.setTenantId(0L);
        version.setArtifactId(artifactId);
        version.setVersionNo(versionNo);
        version.setInfraFileId(infraFileId);
        version.setAvailabilityVersion(0);
        version.setSha256(String.valueOf(versionNo).repeat(64));
        version.setSizeBytes(128L + versionNo);
        version.setDeclaredMediaType("application/pdf");
        version.setDetectedMediaType("application/pdf");
        version.setScanStatusCode("PASSED");
        version.setScanProviderCode("CLAMAV");
        version.setScanProviderVersion("1");
        version.setAvailabilityStatusCode("AVAILABLE");
        version.setCreatedBy(9L);
        version.setCreatedAt(createdAt);
        assertEquals(1, versionMapper.insert(version));
    }

    private Long insertReference(String key, int versionNo, LocalDateTime createdAt) {
        FileReferenceDO reference = new FileReferenceDO();
        reference.setTenantId(0L);
        reference.setOwnerContext("SOL");
        reference.setObjectType("CONSTRUCTION_PLAN_CHANGE");
        reference.setObjectId("99001");
        reference.setPurposeCode("CUSTOMER_DELAY_EVIDENCE");
        reference.setReferenceKey(key);
        reference.setArtifactId(artifactId);
        reference.setFileVersionNo(versionNo);
        reference.setSensitivityCode("INTERNAL");
        reference.setStatusCode("ACTIVE");
        reference.setScopeVersion(8L);
        reference.setVersion(0);
        reference.setCreator("9");
        reference.setUpdater("9");
        reference.setCreateTime(createdAt);
        reference.setUpdateTime(createdAt);
        assertEquals(1, referenceMapper.insert(reference));
        return reference.getId();
    }

    private FileQueryService.ArtifactQuery query(String referenceKey) {
        return new FileQueryService.ArtifactQuery(artifactId, "SOL", "CONSTRUCTION_PLAN_CHANGE",
                "99001", "CUSTOMER_DELAY_EVIDENCE", referenceKey);
    }

    private FileAccessTicketService.AccessCommand accessCommand(String action) {
        return new FileAccessTicketService.AccessCommand(0L, 9L, artifactId, 2, action,
                "SOL", "CONSTRUCTION_PLAN_CHANGE", "99001", "CUSTOMER_DELAY_EVIDENCE", "slot-a");
    }

    private FileBusinessObjectPolicyFact policy() {
        return new FileBusinessObjectPolicyFact(true, 8L, "MUTABLE", "MULTIPLE",
                Set.of("CUSTOMER_EVIDENCE"), Set.of("application/pdf"), 52_428_800L, "INTERNAL");
    }

    private long count(String table, String column, Object value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE tenant_id=0 AND " + column + "=?", Long.class, value);
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
            FileArtifactApiImpl.class,
            cn.iocoder.yudao.module.pms.platform.service.file.ExistingFileVersionAttachmentService.class,
            cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory.class,
            cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter.class,
            FileQueryService.class, FileAccessTicketService.class,
            OperationAuditApiImpl.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean FileBusinessObjectPolicyRegistry policyRegistry() { return mock(FileBusinessObjectPolicyRegistry.class); }
        @Bean FileStorageReceiptApi storageReceiptApi() { return mock(FileStorageReceiptApi.class); }
        @Bean SecurityFrameworkService securityFrameworkService() { return mock(SecurityFrameworkService.class); }
    }
}
