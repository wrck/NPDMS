package cn.iocoder.yudao.module.pms.platform.file;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApiImpl;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageStoreCommand;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.service.file.FileConfigService;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadCompensationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadTerminateCommand;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = FileUploadCompensationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FileUploadCompensationMySqlIntegrationTest {

    @Resource FileUploadCompensationService service;
    @Resource FileStorageReceiptApi storageApi;
    @Resource FileUploadSessionMapper sessionMapper;
    @Resource FileConfigService fileConfigService;
    @Resource JdbcTemplate jdbcTemplate;

    private final FileClient fileClient = mock(FileClient.class);
    private Long sessionId;
    private Long artifactId;
    private String operationId;
    private String storagePath;

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
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        sessionId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        artifactId = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        operationId = "cmp" + suffix.substring(0, 24);
        storagePath = "pms-storage-receipts/" + operationId;
        reset(fileConfigService, fileClient);
        when(fileConfigService.getMasterFileClient()).thenReturn(fileClient);
        when(fileConfigService.getFileClient(601L)).thenReturn(fileClient);
        when(fileClient.getId()).thenReturn(601L);
        when(fileClient.upload(any(), eq(storagePath), eq("application/pdf")))
                .thenReturn("https://private/" + storagePath);
        insertSession();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM plt_file_version WHERE tenant_id=0 AND artifact_id=?", artifactId);
        jdbcTemplate.update("DELETE FROM plt_file_artifact WHERE tenant_id=0 AND id=?", artifactId);
        jdbcTemplate.update("DELETE FROM plt_file_upload_session WHERE tenant_id=0 AND id=?", sessionId);
        jdbcTemplate.update("DELETE FROM infra_file WHERE path=?", storagePath);
    }

    @Test
    void reusesTheRollbackReceiptThenDeletesOnlyAfterFinalTermination() throws Exception {
        var first = storageApi.store(storeCommand());
        var replay = storageApi.store(storeCommand());
        assertEquals(first.infraFileId(), replay.infraFileId());

        service.terminate(command());

        assertEquals("FAILED_FINAL", jdbcTemplate.queryForObject(
                "SELECT status_code FROM plt_file_upload_session WHERE tenant_id=0 AND id=?",
                String.class, sessionId));
        assertEquals(0L, activeStorageRows());
        verify(fileClient).delete(storagePath);
    }

    @Test
    void refusesToDeleteAReceiptReferencedByACommittedVersion() throws Exception {
        var receipt = storageApi.store(storeCommand());
        insertCommittedVersion(receipt.infraFileId());

        assertThrows(RuntimeException.class, () -> service.terminate(command()));

        assertEquals("FAILED_FINAL", jdbcTemplate.queryForObject(
                "SELECT status_code FROM plt_file_upload_session WHERE tenant_id=0 AND id=?",
                String.class, sessionId));
        assertEquals(1L, activeStorageRows());
        verify(fileClient, never()).delete(storagePath);
    }

    private void insertSession() {
        FileUploadSessionDO row = new FileUploadSessionDO();
        row.setId(sessionId);
        row.setModeCode("CREATE_ARTIFACT");
        row.setOwnerContext("SOL");
        row.setObjectType("DURATION_CHANGE");
        row.setObjectId("change-1");
        row.setPurposeCode("CUSTOMER_EVIDENCE");
        row.setReferenceKey("slot-a");
        row.setFileName("evidence.pdf");
        row.setCategoryCode("CUSTOMER_EVIDENCE");
        row.setDeclaredSizeBytes(3L);
        row.setDeclaredMediaType("application/pdf");
        row.setStorageOperationId(operationId);
        row.setStatusCode("INITIALIZED");
        row.setScopeVersion(1L);
        row.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        row.setVersion(0);
        row.setArtifactId(artifactId);
        row.setCreator("9");
        row.setUpdater("9");
        row.setTenantId(0L);
        assertEquals(1, sessionMapper.insert(row));
    }

    private void insertCommittedVersion(Long infraFileId) {
        jdbcTemplate.update("INSERT INTO plt_file_artifact"
                        + "(id,name,category_code,owner_context,lifecycle_status_code,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,?,?,0,'9','9',b'0',0)",
                artifactId, "evidence.pdf", "CUSTOMER_EVIDENCE", "SOL", "ACTIVE");
        jdbcTemplate.update("INSERT INTO plt_file_version"
                        + "(artifact_id,version_no,infra_file_id,availability_version,sha256,size_bytes,"
                        + "declared_media_type,detected_media_type,scan_status_code,scan_provider_code,"
                        + "scan_provider_version,availability_status_code,created_by,created_at,tenant_id) "
                        + "VALUES (?,?,?,0,?,?,?,?,'PASSED','CLAMAV','1','AVAILABLE',9,CURRENT_TIMESTAMP(3),0)",
                artifactId, 1, infraFileId, "a".repeat(64), 3L,
                "application/pdf", "application/pdf");
    }

    private FileStorageStoreCommand storeCommand() {
        return new FileStorageStoreCommand(operationId, new byte[]{1, 2, 3},
                "evidence.pdf", "application/pdf");
    }

    private FileUploadTerminateCommand command() {
        return new FileUploadTerminateCommand(0L, 9L, sessionId, "USER_TERMINATED");
    }

    private long activeStorageRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM infra_file WHERE path=? AND deleted=b'0'", Long.class, storagePath);
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
        } catch (Exception exception) {
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
            "cn.iocoder.yudao.module.infra.dal.mysql.file"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            FileStorageReceiptApiImpl.class, FileUploadCompensationService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean FileConfigService fileConfigService() { return mock(FileConfigService.class); }
    }
}
