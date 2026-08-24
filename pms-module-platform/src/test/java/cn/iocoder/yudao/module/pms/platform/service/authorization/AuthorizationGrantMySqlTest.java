package cn.iocoder.yudao.module.pms.platform.service.authorization;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageResult;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantRevokeCommand;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = AuthorizationGrantMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthorizationGrantMySqlTest {

    private static final String KEY_PREFIX = "it-fproj003-";
    private static final long SUBJECT_BASE = 9_930_000L;

    @Resource AuthorizationGrantService service;
    @Resource JdbcTemplate jdbcTemplate;
    private long subjectId;
    private long resourceId;

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
        TenantContextHolder.setTenantId(0L);
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000L);
        subjectId = SUBJECT_BASE + suffix;
        resourceId = 893_000_000L + suffix;
        cleanFacts();
    }

    @AfterEach
    void tearDown() {
        try {
            cleanFacts();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void lifecycleHonorsEffectiveWindowAndRetainsRevokedHistory() {
        LocalDateTime now = LocalDateTime.now();
        AuthorizationGrantDTO created = service.create(createCommand(
                "lifecycle", "PROJECT_VIEW", "CURRENT_PROJECT", now.minusHours(1), now.plusHours(1)));

        assertNotNull(created.id());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_authorization_grant WHERE id = ?", Long.class, created.id()));
        assertEquals(1, effectiveAt(now).size());
        assertEquals(0, effectiveAt(now.plusHours(2)).size());

        AuthorizationGrantRevokeCommand revoke = revokeCommand(created.id(), 0, "lifecycle-revoke");
        AuthorizationGrantDTO revoked = service.revoke(revoke);
        AuthorizationGrantDTO replay = service.revoke(revoke);

        assertEquals("REVOKED", revoked.statusCode());
        assertEquals(1, revoked.version());
        assertEquals(revoked, replay);
        assertEquals(0, effectiveAt(now).size());
        assertEquals(1L, countGrantRows());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_authorization_grant WHERE id = ? AND current_marker = 1",
                Long.class, created.id()));
    }

    @Test
    void idempotentReplayReturnsOriginalAndDifferentDigestConflicts() {
        LocalDateTime now = LocalDateTime.now();
        AuthorizationGrantCreateCommand command = createCommand(
                "replay", "PROJECT_MANAGE", "PROJECT_AND_DESCENDANTS", now, now.plusDays(1));

        AuthorizationGrantDTO first = service.create(command);
        AuthorizationGrantDTO replay = service.create(command);

        assertEquals(first.id(), replay.id());
        assertEquals(1L, countGrantRows());
        AuthorizationGrantCreateCommand conflict = new AuthorizationGrantCreateCommand(
                command.tenantId(), command.actorId(), command.idempotencyKey(), "b".repeat(64),
                command.subjectTypeCode(), command.subjectId(), command.resourceContextCode(),
                command.resourceTypeCode(), command.resourceId(), command.actionCode(), command.scopeCode(),
                command.effectiveFrom(), command.effectiveTo(), command.sourceContextCode(),
                command.sourceObjectType(), command.sourceObjectId(), command.reason());
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.create(conflict));
        assertEquals("IDEMPOTENCY_CONFLICT", failure.getMessage());
        assertEquals(1L, countGrantRows());
    }

    @Test
    void expiredCurrentFactIsClosedBeforeNewGrantUsesSameKey() {
        LocalDateTime now = LocalDateTime.now();
        service.create(createCommand("expired", "PROJECT_VIEW", "CURRENT_PROJECT",
                now.minusDays(2), now.minusDays(1)));

        AuthorizationGrantDTO current = service.create(createCommand("renewed", "PROJECT_VIEW",
                "CURRENT_PROJECT", now.minusMinutes(1), now.plusDays(1)));

        assertEquals("ACTIVE", current.statusCode());
        assertEquals(2L, countGrantRows());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_authorization_grant WHERE subject_id = ? AND status_code = 'EXPIRED'",
                Long.class, subjectId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_authorization_grant WHERE subject_id = ? AND current_marker = 1",
                Long.class, subjectId));
    }

    @Test
    void concurrentDuplicateGrantCreatesOnlyOneCurrentFact() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> createAfter(start, createCommand(
                            "concurrent-a", "PROJECT_MANAGE", "PROJECT_AND_DESCENDANTS", now, now.plusDays(1)))),
                    executor.submit(() -> createAfter(start, createCommand(
                            "concurrent-b", "PROJECT_MANAGE", "PROJECT_AND_DESCENDANTS", now, now.plusDays(1)))));
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }

            assertEquals(1, outcomes.stream().filter(AuthorizationGrantDTO.class::isInstance).count());
            assertEquals(1, outcomes.stream().filter(Throwable.class::isInstance).count());
            assertEquals(1L, countGrantRows());
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plt_authorization_grant WHERE subject_id = ? AND current_marker = 1",
                    Long.class, subjectId));
        }
    }

    @Test
    void revokeVersionConflictKeepsCurrentFactUnchanged() {
        LocalDateTime now = LocalDateTime.now();
        AuthorizationGrantDTO created = service.create(createCommand(
                "version", "PROJECT_VIEW", "CURRENT_PROJECT", now, now.plusDays(1)));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.revoke(revokeCommand(created.id(), 1, "wrong-version")));

        assertEquals("AUTHORIZATION_GRANT_VERSION_CONFLICT", failure.getMessage());
        assertEquals(1, effectiveAt(now).size());
        assertEquals(1L, countGrantRows());
    }

    @Test
    void pageFiltersCurrentAndHistoricalFactsWithStableOrder() {
        LocalDateTime now = LocalDateTime.now();
        AuthorizationGrantDTO current = service.create(createCommand(
                "page-current", "PROJECT_MANAGE", "PROJECT_AND_DESCENDANTS",
                now.minusMinutes(1), now.plusDays(1)));
        AuthorizationGrantDTO historical = service.create(createCommand(
                "page-history", "PROJECT_VIEW", "CURRENT_PROJECT",
                now.minusMinutes(1), now.plusDays(1)));
        service.revoke(revokeCommand(historical.id(), 0, "page-revoke"));

        AuthorizationGrantPageResult page = service.page(new AuthorizationGrantPageQuery(
                0L, "USER", subjectId, "PROJ", "PROJECT", resourceId,
                null, null, null, null, 1, 20));
        AuthorizationGrantPageResult revoked = service.page(new AuthorizationGrantPageQuery(
                0L, "USER", subjectId, "PROJ", "PROJECT", resourceId,
                "PROJECT_VIEW", "CURRENT_PROJECT", "REVOKED", null, 1, 20));

        assertEquals(2, page.total());
        assertEquals(List.of(historical.id(), current.id()),
                page.list().stream().map(AuthorizationGrantDTO::id).toList());
        assertEquals(1, revoked.total());
        assertEquals(historical.id(), revoked.list().getFirst().id());
    }

    @Test
    void databaseRejectsEmptyEffectiveInterval() {
        LocalDateTime now = LocalDateTime.now();

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO plt_authorization_grant "
                        + "(id,subject_type_code,subject_id,resource_context_code,resource_type_code,resource_id,"
                        + "action_code,scope_code,effective_from,effective_to,status_code,source_context_code,"
                        + "granted_by,granted_at,version,current_marker,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,1,0)",
                893_900_000L + subjectId, "USER", subjectId, "PROJ", "PROJECT", resourceId,
                "PROJECT_VIEW", "CURRENT_PROJECT", now, now, "ACTIVE", "PROJ", 9_900_003L, now));
    }

    private Object createAfter(CountDownLatch start, AuthorizationGrantCreateCommand command) {
        try {
            start.await();
            TenantContextHolder.setTenantId(0L);
            return service.create(command);
        } catch (Throwable failure) {
            return failure;
        } finally {
            TenantContextHolder.clear();
        }
    }

    private List<AuthorizationGrantDTO> effectiveAt(LocalDateTime effectiveAt) {
        return service.listEffective(new AuthorizationGrantQuery(
                0L, "USER", subjectId, "PROJ", "PROJECT", Set.of(resourceId),
                "PROJECT_VIEW", effectiveAt));
    }

    private AuthorizationGrantCreateCommand createCommand(
            String suffix, String actionCode, String scopeCode,
            LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        return new AuthorizationGrantCreateCommand(
                0L, 9_900_003L, KEY_PREFIX + subjectId + "-" + suffix, sha256(suffix),
                "USER", subjectId, "PROJ", "PROJECT", resourceId, actionCode, scopeCode,
                effectiveFrom, effectiveTo, "PROJ", "PROJECT", String.valueOf(resourceId), "集成测试");
    }

    private AuthorizationGrantRevokeCommand revokeCommand(Long grantId, int version, String suffix) {
        return new AuthorizationGrantRevokeCommand(
                0L, 9_900_003L, grantId, version, "撤销集成测试授权",
                KEY_PREFIX + subjectId + "-" + suffix, sha256(suffix));
    }

    private long countGrantRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_authorization_grant WHERE subject_id = ?", Long.class, subjectId);
    }

    private void cleanFacts() {
        jdbcTemplate.update("DELETE FROM plt_authorization_grant WHERE subject_id = ?", subjectId);
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE correlation_id LIKE ?",
                KEY_PREFIX + subjectId + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?",
                KEY_PREFIX + subjectId + "%");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
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
    @MapperScan("cn.iocoder.yudao.module.pms.platform.dal.mysql")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, AuthorizationGrantService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
