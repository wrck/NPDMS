package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyImportMySqlIntegrationTest;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "entityCapabilityMigration", matches = "true")
@SpringBootTest(classes = {SiteSurveyImportMySqlIntegrationTest.Application.class,
        RequirementAnalysisImportMySqlIntegrationTest.RequirementConfig.class}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RequirementAnalysisImportMySqlIntegrationTest {
    @Resource RequirementAnalysisImportService service;
    @Resource PermissionApi permissions;
    @Resource JdbcTemplate jdbc;
    private final EntityActor actor = new EntityActor(1L, 1L, "requirement-analysis-entity-import-acceptance");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (!"npdms_test".equals(System.getenv("NPDMS_DB_NAME")) || !"23316".equals(System.getenv("NPDMS_MYSQL_PORT")))
            throw new IllegalStateException("Requirement import requires npdms_test:23316");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> required("NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required("NPDMS_DB_PASSWORD"));
        registry.add("mybatis-plus.mapper-locations", () -> "classpath*:mapper/**/*.xml");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach void login() {
        assertEquals("npdms_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        TenantContextHolder.setTenantId(actor.tenantId());
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(actor.userId()).setUserType(2), new MockHttpServletRequest());
        when(permissions.hasAnyRoles(eq(actor.userId()), any(String[].class))).thenReturn(true);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }

    @Test void importEveryLegacyObjectAndRetryWithoutOverwritingSourcesOrCreatingDuplicateReferences() {
        var before = jdbc.queryForList("SELECT * FROM sol_preparation WHERE tenant_id=1 AND preparation_type_code='PRE_04_REQUIREMENT_ANALYSIS' ORDER BY id");
        assertFalse(before.isEmpty());
        var projects = before.stream().map(row -> ((Number) row.get("project_id")).longValue()).distinct().toList();
        var failures = new LinkedHashMap<Long, String>();
        for (Long project : projects) {
            try { service.importProject(project, actor); }
            catch (RuntimeException failure) { failures.put(project, failure.getMessage()); }
        }
        assertTrue(failures.isEmpty(), failures.toString());
        long references = references();
        for (Long project : projects) assertFalse(service.importProject(project, actor).created());
        assertEquals(references, references());
        assertEquals(before, jdbc.queryForList("SELECT * FROM sol_preparation WHERE tenant_id=1 AND preparation_type_code='PRE_04_REQUIREMENT_ANALYSIS' ORDER BY id"));
        assertEquals(before.size(), jdbc.queryForObject("SELECT COUNT(*) FROM sol_requirement_analysis_revision WHERE tenant_id=1", Integer.class));
        System.out.println("Requirement import reconciled " + projects.size() + " business objects / " + before.size()
                + " revisions / " + references + " file references; retry added nothing and legacy source unchanged.");
    }

    private long references() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_reference WHERE tenant_id=1 AND owner_context='SOL' AND object_type='REQUIREMENT_ANALYSIS_REVISION'", Long.class);
    }

    private static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan("cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement")
    @Import(RequirementAnalysisImportService.class)
    static class RequirementConfig {
        // Import consumes the owner's field catalog. Online version workflows are tested separately.
        @Bean EntityFieldProvider requirementFields() {
            var fields = mock(EntityFieldProvider.class);
            when(fields.ownerModule()).thenReturn("SOL");
            when(fields.entityType()).thenReturn("REQUIREMENT_ANALYSIS");
            when(fields.fields()).thenReturn(RequirementAnalysisEntityProvider.FIELDS.fields());
            return fields;
        }
    }
}
