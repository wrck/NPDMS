package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialDTO;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = DeviceCredentialMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeviceCredentialMySqlTest {

    private static final String CODE_PREFIX = "it-device-credential-";

    @Resource DeviceCredentialService service;
    @Resource JdbcTemplate jdbcTemplate;
    private String credentialCode;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
        registry.add("mybatis-plus.encryptor.password", () -> "integration-test-key");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        credentialCode = CODE_PREFIX + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM plt_credential_grant WHERE credential_id IN "
                    + "(SELECT id FROM plt_device_credential WHERE credential_code = ?)", credentialCode);
            jdbcTemplate.update("DELETE FROM plt_device_credential WHERE credential_code = ?", credentialCode);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void persistsCiphertextAndRequiresExactGrantForResolution() {
        char[] secret = "database-secret".toCharArray();
        DeviceCredentialDTO created = service.create(new DeviceCredentialCreateCommand(
                0L, 9001L, credentialCode, "SSH", "operator", secret, null,
                "device-1", "template-1", LocalDateTime.parse("2026-08-29T10:00:00")));

        String stored = jdbcTemplate.queryForObject(
                "SELECT encrypted_secret FROM plt_device_credential WHERE id = ?", String.class, created.id());
        assertFalse(stored.contains("database-secret"));
        assertTrue(allZero(secret));
        assertEquals("template-1", jdbcTemplate.queryForObject(
                "SELECT command_template_id FROM plt_credential_grant WHERE id = ?", String.class,
                created.defaultGrantId()));

        try (DeviceCredentialService.ResolvedCredential resolved = service.resolve(
                new DeviceCredentialService.CredentialAccessRequest(
                        0L, 9001L, created.id(), "device-1", "SSH", "template-1"))) {
            assertEquals("database-secret", new String(resolved.secret()));
        }
        assertThrows(IllegalStateException.class, () -> service.resolve(
                new DeviceCredentialService.CredentialAccessRequest(
                        0L, 9001L, created.id(), "device-1", "SSH", "template-other")));
    }

    private static boolean allZero(char[] value) {
        for (char item : value) {
            if (item != '\0') return false;
        }
        return true;
    }

    private static Map<String, String> currentEnvironment() {
        Map<String, String> values = new LinkedHashMap<>(System.getenv());
        for (Path directory = Path.of("").toAbsolutePath().normalize(); directory != null; directory = directory.getParent()) {
            Path dotenv = directory.resolve(".env");
            if (!Files.isRegularFile(dotenv)) continue;
            try {
                for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) continue;
                    int separator = trimmed.indexOf('=');
                    values.putIfAbsent(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
                }
                break;
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("无法读取当前仓库.env", ex);
            }
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("真实MySQL集成测试缺少当前仓库参数：" + key);
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan("cn.iocoder.yudao.module.pms.platform.dal.mysql")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            MybatisCredentialSecretProtector.class, DeviceCredentialService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean Clock clock() { return Clock.fixed(java.time.Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC); }
    }
}
