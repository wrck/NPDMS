package cn.iocoder.yudao.module.infra.api.file;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageStoreCommand;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.service.file.FileConfigService;
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
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_STORAGE_RECEIPT_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = FileStorageReceiptMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FileStorageReceiptMySqlIntegrationTest {

    @Resource FileStorageReceiptApi api;
    @Resource FileConfigService fileConfigService;
    @Resource JdbcTemplate jdbcTemplate;

    private final FileClient clientA = mock(FileClient.class);
    private final FileClient clientB = mock(FileClient.class);
    private String operationId;
    private String storagePath;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.infra");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() throws Exception {
        operationId = "it" + UUID.randomUUID().toString().replace("-", "");
        storagePath = FileStorageReceiptApiImpl.buildStoragePath(operationId);
        reset(fileConfigService, clientA, clientB);
        when(clientA.getId()).thenReturn(101L);
        when(clientB.getId()).thenReturn(202L);
        when(fileConfigService.getMasterFileClient()).thenReturn(clientA);
        when(fileConfigService.getFileClient(101L)).thenReturn(clientA);
        when(fileConfigService.getFileClient(202L)).thenReturn(clientB);
        when(clientA.upload(any(), eq(storagePath), eq("application/pdf")))
                .thenReturn("https://storage-a/" + storagePath);
        when(clientA.presignGetUrl(storagePath, 120)).thenReturn("https://signed-a/file");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM infra_file WHERE path=?", storagePath);
    }

    @Test
    void replaysAccessesAndDeletesTheFrozenStorageAfterMasterSwitch() throws Exception {
        FileStorageStoreCommand command = command();
        var first = api.store(command);
        assertTrue(first.infraFileId() > 0L);
        assertEquals(101L, jdbcTemplate.queryForObject(
                "SELECT config_id FROM infra_file WHERE id=?", Long.class, first.infraFileId()));

        when(fileConfigService.getMasterFileClient()).thenReturn(clientB);
        clearInvocations(clientA, clientB, fileConfigService);
        var replay = api.store(command);
        assertEquals(first.infraFileId(), replay.infraFileId());
        verify(fileConfigService, never()).getMasterFileClient();
        verify(clientB, never()).upload(any(), any(), any());

        assertEquals("https://signed-a/file", api.presignGet(first.infraFileId(), 120).shortLivedUrl());
        api.delete(operationId);
        verify(clientA).delete(storagePath);
        assertEquals(0L, activeRows());
    }

    @Test
    void rejectsDuplicateCrossConfigReceiptsWithoutChoosingOne() {
        api.store(command());
        jdbcTemplate.update("INSERT INTO infra_file(config_id,name,path,url,type,size,deleted) "
                        + "VALUES (?,?,?,?,?,?,b'0')",
                202L, "evidence.pdf", storagePath, "https://storage-b/" + storagePath,
                "application/pdf", 3L);

        ServiceException storeFailure = assertThrows(ServiceException.class, () -> api.store(command()));
        assertEquals(FILE_STORAGE_RECEIPT_CONFLICT.getCode(), storeFailure.getCode());
        ServiceException deleteFailure = assertThrows(ServiceException.class, () -> api.delete(operationId));
        assertEquals(FILE_STORAGE_RECEIPT_CONFLICT.getCode(), deleteFailure.getCode());
        assertEquals(2L, activeRows());
    }

    private FileStorageStoreCommand command() {
        return new FileStorageStoreCommand(operationId, new byte[]{1, 2, 3},
                "evidence.pdf", "application/pdf");
    }

    private long activeRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM infra_file WHERE path=? AND deleted=b'0'", Long.class, storagePath);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.infra.dal.mysql.file")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            FileStorageReceiptApiImpl.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean FileConfigService fileConfigService() { return mock(FileConfigService.class); }
    }
}
