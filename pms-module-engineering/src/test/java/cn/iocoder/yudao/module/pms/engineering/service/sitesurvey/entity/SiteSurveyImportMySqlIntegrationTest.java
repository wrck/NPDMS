package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import cn.iocoder.yudao.module.pms.platform.service.entity.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
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
import org.springframework.boot.jdbc.autoconfigure.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Explicit handover acceptance against the isolated database; legacy rows are never written. */
@EnabledIfSystemProperty(named = "entityCapabilityMigration", matches = "true")
@SpringBootTest(classes = SiteSurveyImportMySqlIntegrationTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class SiteSurveyImportMySqlIntegrationTest {
    @Resource SiteSurveyImportService service;
    @Resource JdbcTemplate jdbc;
    @Resource TransactionTemplate transactions;
    @Resource PermissionApi permissions;
    @Resource ProjectScopeApi scopes;
    private final EntityActor actor = new EntityActor(1L, 1L, "site-survey-entity-import-acceptance");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (!"npdms_test".equals(System.getenv("NPDMS_DB_NAME"))
                || !"23316".equals(System.getenv("NPDMS_MYSQL_PORT"))) {
            throw new IllegalStateException("Survey migration requires isolated npdms_test:23316");
        }
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:23316/npdms_test"
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> required("NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required("NPDMS_DB_PASSWORD"));
        registry.add("mybatis-plus.mapper-locations", () -> "classpath*:mapper/**/*.xml");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setup() {
        assertEquals("npdms_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        TenantContextHolder.setTenantId(actor.tenantId());
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(actor.userId()).setUserType(2), new MockHttpServletRequest());
        when(permissions.hasAnyRoles(eq(actor.userId()), any(String[].class))).thenReturn(true);
        when(permissions.hasAnyPermissions(eq(actor.userId()), any(String[].class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenAnswer(invocation -> {
            ProjectCurrentScopeQuery query = invocation.getArgument(0);
            return new ProjectScopeResult(query.anchorProjectId(), 1L, Set.of(query.anchorProjectId()), Set.of());
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void importAndRetryEveryExistingSurveyWithExactContentReconciliation() {
        var sourceBefore = jdbc.queryForList("SELECT * FROM sol_site_survey WHERE tenant_id=1 ORDER BY id");
        assertFalse(sourceBefore.isEmpty());
        var ids = sourceBefore.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        var failures = new LinkedHashMap<Long, String>();
        for (Long id : ids) {
            try { service.importOne(id, actor); }
            catch (RuntimeException failure) { failures.put(id, failure.getMessage()); }
        }
        assertTrue(failures.isEmpty(), failures.toString());
        long definitions = count("plt_entity_extension_definition");
        long values = count("plt_entity_extension_value");
        long bindings = count("plt_entity_form_binding");
        for (Long id : ids) assertFalse(service.importOne(id, actor).created());
        assertEquals(definitions, count("plt_entity_extension_definition"));
        assertEquals(values, count("plt_entity_extension_value"));
        assertEquals(bindings, count("plt_entity_form_binding"));
        assertEquals(sourceBefore, jdbc.queryForList("SELECT * FROM sol_site_survey WHERE tenant_id=1 ORDER BY id"));
        // A changed target must be reported. Its content and the source must never be replaced on retry.
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE sol_site_survey SET power_supply='IMPORT_CONFLICT' WHERE tenant_id=1 AND id=?", ids.getFirst());
            var conflict = assertThrows(IllegalStateException.class, () -> service.importOne(ids.getFirst(), actor));
            assertTrue(conflict.getMessage().contains("powerSupply"));
            assertEquals("IMPORT_CONFLICT", jdbc.queryForObject("SELECT power_supply FROM sol_site_survey WHERE tenant_id=1 AND id=?",
                    String.class, ids.getFirst()));
            status.setRollbackOnly();
        });
        System.out.println("Survey import reconciled and retried " + ids.size() + " objects; definitions=" + definitions
                + ", extension rows=" + values + ", form bindings=" + bindings + "; source unchanged.");
    }

    private long count(String table) {
        return switch (table) {
            case "plt_entity_extension_definition" -> jdbc.queryForObject("SELECT COUNT(*) FROM plt_entity_extension_definition WHERE tenant_id=1 AND entity_type='SITE_SURVEY'", Long.class);
            case "plt_entity_extension_value" -> jdbc.queryForObject("SELECT COUNT(*) FROM plt_entity_extension_value WHERE tenant_id=1 AND entity_type='SITE_SURVEY'", Long.class);
            case "plt_entity_form_binding" -> jdbc.queryForObject("SELECT COUNT(*) FROM plt_entity_form_binding WHERE tenant_id=1 AND entity_type='SITE_SURVEY'", Long.class);
            default -> throw new IllegalArgumentException(table);
        };
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.entity",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.file",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, SiteSurveyImportService.class,
            SiteSurveyEntityProvider.class, SiteSurveyDetails.class, EntityProviderRegistry.class,
            EntityCapabilityImportService.class, DynamicFormSchemaService.class, OperationAuditApiImpl.class})
    public static class Application {
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
        @Bean PermissionApi permissionApi() { return mock(PermissionApi.class); }
        @Bean ProjectScopeApi projectScopeApi() { return mock(ProjectScopeApi.class); }
        @Bean TenantLineInnerInterceptor tenantInterceptor(MybatisPlusInterceptor interceptor) {
            var tenant = new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, tenant, 0);
            return tenant;
        }
    }
}
