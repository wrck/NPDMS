package cn.iocoder.yudao.module.pms.platform.businessview.application;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOperationAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOperationAuditMapper;
import cn.iocoder.yudao.module.pms.platform.service.businessview.*;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import static cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewErrors.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PM-03: real MyBatis + command/idempotency/audit transactions. Opt-in, fixed 23316/npdms_test.
 * Uses an exclusive creator and UUID prefix, retains its own test evidence; no DELETE, trigger,
 * migration, clean/reset, or mutation of unrelated rows. PAGE fixture is test-only, not a deployed
 * production capability or a claim that the real SOL page has passed acceptance.
 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = BusinessViewMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BusinessViewMySqlIntegrationTest {
    private static final long TENANT = 0L;
    private static final long ACTOR = 9_930_003L;
    @Resource BusinessViewApplicationService service;
    @Resource JdbcTemplate jdbc;
    @Resource PermissionApi permissions;
    @Resource TransactionTemplate transactions;
    @MockitoSpyBean PlatformOperationAuditMapper auditMapper;
    private String prefix;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Map<String, String> env = System.getenv();
        String port = env.getOrDefault("NPDMS_MYSQL_PORT", "23316");
        String database = env.getOrDefault("NPDMS_DB_NAME", "npdms_test");
        if (!"23316".equals(port) || !"npdms_test".equals(database))
            throw new IllegalStateException("Business-view IT only accepts approved 23316/npdms_test");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:23316/npdms_test"
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&nullCatalogMeansCurrent=true");
        registry.add("spring.datasource.username", () -> required(env, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(env, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }
    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }
    @BeforeEach void setup() {
        prefix = "BVIT_" + UUID.randomUUID().toString().replace("-", "");
        authenticate(TENANT);
        reset(permissions);
        when(permissions.hasAnyPermissions(anyLong(), anyString())).thenReturn(true);
        assertEquals("npdms_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
    }
    @AfterEach void cleanupContextOnly() {
        reset(auditMapper);
        SecurityContextHolder.clearContext(); TenantContextHolder.clear();
    }
    private static void authenticate(long tenant) {
        TenantContextHolder.setTenantId(tenant);
        var login = new LoginUser(); login.setId(ACTOR); login.setUserType(2);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
    }
    private String key(String action) { return prefix + "_" + action; }
    private BusinessViewApplicationService.Selection selection(String suffix) {
        return new BusinessViewApplicationService.Selection("BUSINESS_VIEW_IT", key(suffix), "BUSINESS_VIEW_IT_PAGE", "1", null);
    }
    private void error(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ServiceException.class, action).getCode());
    }
    private long auditCount(String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? AND actor_id=? AND correlation_id=?",
                Long.class, TENANT, ACTOR, key);
    }
    private long commandCount(String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND actor_id=? AND idempotency_key=?",
                Long.class, TENANT, ACTOR, key);
    }

    @Test void realLifecycleReplayIsolationAndRowCas() {
        var draft = service.create(key("create"), selection("view"));
        assertEquals(draft.id(), service.create(key("create"), selection("view")).id());
        assertEquals(1L, auditCount(key("create"))); assertEquals(1L, commandCount(key("create")));
        error(KEY_CONFLICT.getCode(), () -> service.create(key("create"), selection("different")));
        var edited = service.update(draft.id(), 0, key("update"), selection("view"));
        assertEquals(1, edited.version());
        error(VERSION_CONFLICT.getCode(), () -> service.publish(draft.id(), 0, key("stale")));
        assertEquals(0L, auditCount(key("stale"))); assertEquals(0L, commandCount(key("stale")));
        var published = service.publish(draft.id(), 1, key("publish"));
        assertEquals(2, published.version());
        error(STATE_INVALID.getCode(), () -> service.update(draft.id(), 2, key("immutable"), selection("view")));
        var copied = service.copy(draft.id(), 2, key("copy"));
        assertEquals(2L, copied.revisionNo()); assertEquals("DRAFT", copied.status());
        error(DRAFT_EXISTS.getCode(), () -> service.copy(draft.id(), 2, key("copy_again")));
        var disabled = service.disable(draft.id(), 2, key("disable"));
        assertEquals(3, disabled.version()); assertEquals("DISABLED", disabled.status());
        assertEquals(published.contextSchema(), disabled.contextSchema());
        assertNotNull(service.getRevision(new BusinessViewQueryApi.Query(draft.id(), BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE)));
        error(UNAVAILABLE.getCode(), () -> service.getRevision(new BusinessViewQueryApi.Query(draft.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE)));
        authenticate(987654L);
        error(NOT_FOUND.getCode(), () -> service.get(draft.id()));
        authenticate(TENANT);
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_business_view_revision WHERE id=? AND tenant_id=? AND version=3 AND published_at IS NOT NULL AND disabled_at IS NOT NULL",
                Long.class, draft.id(), TENANT));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? AND aggregate_type='BusinessViewRegistration' AND aggregate_key=?",
                Long.class, TENANT, draft.id().toString()));
    }

    @Test void auditInsertFailureRollsBackPublishedRowAndSuccessReservation() {
        var draft = service.create(key("create"), selection("rollback"));
        doThrow(new DataIntegrityViolationException("test-only audit insert failure"))
                .when(auditMapper).insert(any(PlatformOperationAuditDO.class));
        assertThrows(DataIntegrityViolationException.class, () -> service.publish(draft.id(), 0, key("publish_fail")));
        reset(auditMapper);
        var stored = service.get(draft.id());
        assertEquals("DRAFT", stored.status()); assertEquals(0, stored.version()); assertNull(stored.publishedAt());
        assertEquals(0L, auditCount(key("publish_fail"))); assertEquals(0L, commandCount(key("publish_fail")));
        assertEquals("PUBLISHED", service.publish(draft.id(), 0, key("publish_fail")).status());
    }

    @Test void auditInsertFailureDuringCreationLeavesNoRegistrationOrIdempotentSuccess() {
        doThrow(new DataIntegrityViolationException("test-only audit insert failure"))
                .when(auditMapper).insert(any(PlatformOperationAuditDO.class));
        assertThrows(DataIntegrityViolationException.class, () -> service.create(key("create_fail"), selection("rollback_new")));
        reset(auditMapper);
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_business_view_revision WHERE tenant_id=? AND entity_type='BUSINESS_VIEW_IT' AND view_key=?",
                Long.class, TENANT, key("rollback_new")));
        assertEquals(0L, auditCount(key("create_fail"))); assertEquals(0L, commandCount(key("create_fail")));
    }

    @Test void simultaneousSameExpectedVersionHasExactlyOneSuccessfulPublisher() throws Exception {
        var draft = service.create(key("create"), selection("concurrent"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> first = () -> publishAtBarrier(draft.id(), key("publisher_a"), start);
            Callable<Boolean> second = () -> publishAtBarrier(draft.id(), key("publisher_b"), start);
            Future<Boolean> a = executor.submit(first); Future<Boolean> b = executor.submit(second); start.countDown();
            assertNotEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertEquals(1, service.get(draft.id()).version());
            assertEquals(1L, auditCount(key("publisher_a")) + auditCount(key("publisher_b")));
        } finally { executor.shutdownNow(); }
    }
    private boolean publishAtBarrier(Long id, String key, CountDownLatch start) throws Exception {
        authenticate(TENANT);
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
            service.publish(id, 0, key); return true;
        } catch (ServiceException failure) {
            assertEquals(VERSION_CONFLICT.getCode(), failure.getCode()); return false;
        } finally { SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }
    }

    @Test void internalBatchRequiresTransactionAndKeepsInputOrderAfterStableLocks() {
        var z = service.create(key("zcreate"), selection("z"));
        var a = service.create(key("acreate"), selection("a"));
        service.publish(z.id(), 0, key("zpublish")); service.publish(a.id(), 0, key("apublish"));
        var queries = List.of(new BusinessViewQueryApi.Query(z.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE, 1),
                new BusinessViewQueryApi.Query(a.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE, 1));
        assertThrows(IllegalTransactionStateException.class, () -> service.lockAndRevalidateAll(queries));
        var result = transactions.execute(status -> service.lockAndRevalidateAll(queries));
        assertEquals(List.of(z.id(), a.id()), result.stream().map(BusinessViewRevision::id).toList());
        assertTrue(result.stream().allMatch(row -> row.allowedActions().isEmpty()));
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command", "cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class, MybatisPlusJoinAutoConfiguration.class,
            SpringUtil.class, BusinessViewApplicationService.class, BusinessViewAccess.class,
            BusinessViewComponentRegistry.class, PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
        @Bean PermissionApi permissionApi() { return mock(PermissionApi.class); }
        @Bean BusinessViewComponentProvider testOnlyPageProvider() {
            return new BusinessViewComponentProvider() {
                @Override public Component component() {
                    return new Component("BUSINESS_VIEW_IT", "PLATFORM", ViewSource.PAGE, "BUSINESS_VIEW_IT_PAGE", "1",
                            JsonUtils.parseTree("{\"type\":\"object\"}"), JsonUtils.parseTree("[\"VIEW\"]"),
                            "BUSINESS_VIEW_IT_QUERY", "BUSINESS_VIEW_IT_COMMAND", "BUSINESS_VIEW_IT_PERMISSION", "业务视图集成测试");
                }
                @Override public boolean canConfigure(Context context, ConfigurationAction action) { return context.actorId() == ACTOR; }
                @Override public Dependencies validateConfiguration(Context context, Long revisionId, ValidationMode mode) {
                    if (revisionId != null) throw new IllegalArgumentException("PAGE forbids forms");
                    return new Dependencies(false);
                }
            };
        }
        @Bean TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            var inner = new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0); return inner;
        }
    }
}
