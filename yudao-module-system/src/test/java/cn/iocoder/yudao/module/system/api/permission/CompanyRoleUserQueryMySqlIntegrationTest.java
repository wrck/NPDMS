package cn.iocoder.yudao.module.system.api.permission;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.api.permission.dto.CompanyRoleUserPageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PM-01 / INT-09：只使用固定测试MySQL，所有专用数据逐用例事务回滚。
 * 不继承带H2初始化/全表clean.sql的BaseDbUnitTest，不创建第二套环境。
 */
@SpringBootTest(classes = CompanyRoleUserQueryMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@Transactional
class CompanyRoleUserQueryMySqlIntegrationTest {

    private static final String JDBC_URL = "jdbc:mysql://127.0.0.1:23316/npdms_test"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
            + "&characterEncoding=UTF-8";
    private static final String ROLE = "PROJECT_MANAGER";

    @Resource
    private OrganizationScopeApi api;
    @Resource
    private JdbcTemplate jdbc;

    private String marker;
    private int sequence;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", () -> requiredEnvironment("NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.initial-size", () -> "1");
        registry.add("spring.datasource.druid.max-active", () -> "4");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.system");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void verifyFixedDatabaseBeforeWriting() {
        assertTrue(TestTransaction.isActive(), "测试数据必须在可回滚事务内");
        assertEquals("npdms_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String actualUrl = jdbc.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getURL());
        assertNotNull(actualUrl);
        assertTrue(actualUrl.startsWith("jdbc:mysql://127.0.0.1:23316/npdms_test"),
                "禁止连接第二套环境或开发/生产库");
        TenantContextHolder.setTenantId(1L);
        marker = "pm01q-" + UUID.randomUUID().toString().substring(0, 12);
        sequence = 0;
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @AfterTransaction
    void verifyFixtureRowsWereRolledBack() {
        if (marker == null) return;
        Long remaining = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM system_company WHERE creator=?)
                     + (SELECT COUNT(*) FROM system_users WHERE creator=?)
                     + (SELECT COUNT(*) FROM system_dept WHERE creator=?)
                     + (SELECT COUNT(*) FROM system_user_company_department_scope WHERE creator=?)
                """, Long.class, marker, marker, marker, marker);
        assertEquals(0L, remaining, "本次测试数据必须全部回滚，不清理其他数据");
    }

    @Test
    void matchesCompanyAndRoleOnOneRowAcrossDepartmentsWithoutDuplicates() {
        Company company = company(1L);
        Company otherCompany = company(1L);
        Department firstDepartment = department();
        Department secondDepartment = department();
        long firstUser = user(1L, "first");
        long secondUser = user(1L, "second");
        long mixedUser = user(1L, "mixed");
        scope(firstUser, company, ROLE, firstDepartment, 1L);
        scope(firstUser, company, ROLE, secondDepartment, 1L);
        scope(secondUser, company, ROLE, secondDepartment, 1L);
        scope(mixedUser, company, "ENGINEER", firstDepartment, 1L);
        scope(mixedUser, otherCompany, ROLE, secondDepartment, 1L);

        var result = api.pageCompanyRoleUsers(request(company));

        assertEquals(2L, result.getTotal());
        assertEquals(List.of(firstUser, secondUser), result.getList().stream()
                .map(item -> item.getUserId()).toList());
        assertTrue(result.getList().stream().allMatch(item ->
                company.id() == item.getCompanyId() && ROLE.equals(item.getRoleCode())));
        assertNotNull(result.getList().getFirst().getUsername());
        assertNotNull(result.getList().getFirst().getNickname());
        assertEquals(List.of(mixedUser), api.pageCompanyRoleUsers(request(company).setRoleCode("ENGINEER"))
                .getList().stream().map(item -> item.getUserId()).toList());
        assertEquals(0L, api.pageCompanyRoleUsers(request(company).setRoleCode("UNKNOWN_ROLE")).getTotal());
    }

    @Test
    void excludesDisabledDeletedFutureAndExpiredAuthorizationAndUsers() {
        Company company = company(1L);
        long valid = user(1L, "valid");
        scope(valid, company, ROLE, null, 1L);
        for (String mode : List.of("disabledScope", "deletedScope", "future", "expired",
                "disabledUser", "deletedUser")) {
            long userId = user(1L, mode);
            long scopeId = scope(userId, company, ROLE, null, 1L);
            switch (mode) {
                case "disabledScope" -> jdbc.update(
                        "UPDATE system_user_company_department_scope SET status=1 WHERE id=?", scopeId);
                case "deletedScope" -> jdbc.update(
                        "UPDATE system_user_company_department_scope SET deleted=TRUE WHERE id=?", scopeId);
                case "future" -> jdbc.update(
                        "UPDATE system_user_company_department_scope SET effective_from=? WHERE id=?",
                        LocalDateTime.now().plusDays(1), scopeId);
                case "expired" -> jdbc.update(
                        "UPDATE system_user_company_department_scope SET effective_to=? WHERE id=?",
                        LocalDateTime.now().minusHours(1), scopeId);
                case "disabledUser" -> jdbc.update("UPDATE system_users SET status=1 WHERE id=?", userId);
                case "deletedUser" -> jdbc.update("UPDATE system_users SET deleted=TRUE WHERE id=?", userId);
                default -> throw new AssertionError(mode);
            }
        }

        var result = api.pageCompanyRoleUsers(request(company));

        assertEquals(1L, result.getTotal());
        assertEquals(valid, result.getList().getFirst().getUserId());
    }

    @Test
    void selectedUsersAndEmptySetUseTheSameRuleAndRevalidateRevocation() {
        Company company = company(1L);
        long first = user(1L, "first");
        long second = user(1L, "second");
        long wrongRole = user(1L, "wrong");
        long firstScope = scope(first, company, ROLE, null, 1L);
        scope(second, company, ROLE, null, 1L);
        scope(wrongRole, company, "ENGINEER", null, 1L);
        assertEquals(2L, api.pageCompanyRoleUsers(request(company)).getTotal());
        assertEquals(0L, api.pageCompanyRoleUsers(request(company).setUserIds(Set.of())).getTotal());
        var selected = request(company).setUserIds(Set.of(first, wrongRole));
        assertEquals(List.of(first), api.pageCompanyRoleUsers(selected).getList().stream()
                .map(item -> item.getUserId()).toList());

        jdbc.update("UPDATE system_user_company_department_scope SET status=1 WHERE id=?", firstScope);

        assertEquals(0L, api.pageCompanyRoleUsers(selected).getTotal());
    }

    @Test
    void paginatesByUniqueUserIdAndSupportsKeywordWithoutLosingTotal() {
        Company company = company(1L);
        long first = user(1L, "alpha");
        long second = user(1L, "beta");
        scope(first, company, ROLE, null, 1L);
        scope(first, company, ROLE, department(), 1L);
        scope(second, company, ROLE, null, 1L);
        var query = request(company);
        query.setPageSize(1);
        var firstPage = api.pageCompanyRoleUsers(query);
        query.setPageNo(2);
        var secondPage = api.pageCompanyRoleUsers(query);
        query.setPageNo(3);
        var beyond = api.pageCompanyRoleUsers(query);
        assertEquals(2L, firstPage.getTotal());
        assertEquals(2L, secondPage.getTotal());
        assertEquals(first, firstPage.getList().getFirst().getUserId());
        assertEquals(second, secondPage.getList().getFirst().getUserId());
        assertEquals(2L, beyond.getTotal());
        assertTrue(beyond.getList().isEmpty());
        assertEquals(List.of(second), api.pageCompanyRoleUsers(request(company).setKeyword(" beta "))
                .getList().stream().map(item -> item.getUserId()).toList());
    }

    @Test
    void tenantInterceptorAndTenantJoinsRejectForeignFacts() {
        Company company = company(1L);
        Company foreignCompany = company(2L);
        long local = user(1L, "local");
        long foreign = user(2L, "foreign");
        scope(local, company, ROLE, null, 2L);
        scope(foreign, company, ROLE, null, 1L);
        scope(foreign, foreignCompany, ROLE, null, 2L);
        assertEquals(0L, api.pageCompanyRoleUsers(request(company)).getTotal());
        assertServiceException(() -> api.pageCompanyRoleUsers(request(foreignCompany)),
                ORGANIZATION_SCOPE_INVALID);
        TenantContextHolder.setTenantId(2L);
        assertEquals(List.of(foreign), api.pageCompanyRoleUsers(request(foreignCompany)).getList()
                .stream().map(item -> item.getUserId()).toList());
        assertServiceException(() -> api.pageCompanyRoleUsers(request(company)), ORGANIZATION_SCOPE_INVALID);
    }

    @Test
    void preservesExistingDepartmentCandidateQuery() {
        Company company = company(1L);
        Department firstDepartment = department();
        Department secondDepartment = department();
        long first = user(1L, "first");
        long second = user(1L, "second");
        scope(first, company, ROLE, firstDepartment, 1L);
        scope(second, company, ROLE, secondDepartment, 1L);
        var oldQuery = new OrganizationUserCandidatePageReqDTO().setCompanyId(company.id())
                .setDepartmentId(firstDepartment.id()).setDepartmentCode(firstDepartment.code());

        assertEquals(List.of(first), api.pageActiveUsers(oldQuery).getList().stream()
                .map(item -> item.getUserId()).toList());
        assertTrue(api.hasScope(first, company.id(), firstDepartment.id()));
        assertFalse(api.hasScope(first, company.id(), secondDepartment.id()));
        assertEquals(2L, api.pageCompanyRoleUsers(request(company)).getTotal());
    }

    @Test
    void rejectsInvalidArgumentsWithoutExpandingQueryScope() {
        Company company = company(1L);
        assertServiceException(() -> api.pageCompanyRoleUsers(null), ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        assertServiceException(() -> api.pageCompanyRoleUsers(request(company).setCompanyId(0L)),
                ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        assertServiceException(() -> api.pageCompanyRoleUsers(request(company).setRoleCode(" ")),
                ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        assertServiceException(() -> api.pageCompanyRoleUsers(request(company).setRoleCode("x".repeat(33))),
                ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        assertServiceException(() -> api.pageCompanyRoleUsers(request(company).setKeyword("x".repeat(65))),
                ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        var oversized = request(company);
        oversized.setPageSize(101);
        assertServiceException(() -> api.pageCompanyRoleUsers(oversized), ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        var zeroPage = request(company);
        zeroPage.setPageNo(0);
        assertServiceException(() -> api.pageCompanyRoleUsers(zeroPage), ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        Set<Long> invalidIds = new HashSet<>();
        invalidIds.add(null);
        assertServiceException(() -> api.pageCompanyRoleUsers(request(company).setUserIds(invalidIds)),
                ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        assertServiceException(() -> api.pageCompanyRoleUsers(request(company).setUserIds(Set.of(-1L))),
                ORGANIZATION_SCOPE_INVALID_ARGUMENT);
    }

    @Test
    void rejectsUnavailableCompanyRatherThanReportingNoQualifiedUsers() {
        Company company = company(1L);
        jdbc.update("UPDATE system_company SET status=1 WHERE id=?", company.id());
        assertServiceException(() -> api.pageCompanyRoleUsers(request(company)), ORGANIZATION_SCOPE_INVALID);
        assertServiceException(() -> api.pageCompanyRoleUsers(request(company).setCompanyId(Long.MAX_VALUE)),
                ORGANIZATION_SCOPE_INVALID);
    }

    private Company company(long tenantId) {
        String code = marker + "-c" + sequence++;
        long id = insert("INSERT INTO system_company (tenant_id,code,name,status,version,creator) "
                + "VALUES (?,?,?,0,0,?)", tenantId, code, code, marker);
        return new Company(id, code);
    }

    private Department department() {
        String code = marker + "-d" + sequence++;
        long id = insert("INSERT INTO system_dept (tenant_id,code,name,parent_id,sort,status,creator) "
                + "VALUES (1,?,?,0,0,0,?)", code, code, marker);
        return new Department(id, code);
    }

    private long user(long tenantId, String nickname) {
        return insert("INSERT INTO system_users (tenant_id,username,password,nickname,status,creator) "
                + "VALUES (?,?,?, ?,0,?)", tenantId, marker + "-u" + sequence++, "", nickname, marker);
    }

    private long scope(long userId, Company company, String role, Department department, long tenantId) {
        return insert("""
                INSERT INTO system_user_company_department_scope
                  (tenant_id,user_id,company_id,company_code,company_name,department_id,department_code,
                   department_name,scope_role,is_primary,effective_from,status,version,creator)
                VALUES (?,?,?,?,?,?,?,?,?,FALSE,?,0,0,?)
                """, tenantId, userId, company.id(), company.code(), company.code(),
                department == null ? null : department.id(), department == null ? null : department.code(),
                department == null ? null : department.code(), role, LocalDateTime.now().minusDays(1), marker);
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        }, keys);
        assertNotNull(keys.getKey());
        return keys.getKey().longValue();
    }

    private static CompanyRoleUserPageReqDTO request(Company company) {
        return new CompanyRoleUserPageReqDTO().setCompanyId(company.id()).setRoleCode(ROLE);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少固定测试凭据配置：" + name);
        return value;
    }

    private record Company(long id, String code) { }
    private record Department(long id, String code) { }

    @SpringBootConfiguration
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, OrganizationScopeApiImpl.class, SpringUtil.class})
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, tenant, 0);
            return tenant;
        }
    }
}
