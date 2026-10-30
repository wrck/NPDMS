package cn.iocoder.yudao.module.system.service.permission;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.datapermission.config.YudaoDataPermissionAutoConfiguration;
import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRule;
import cn.iocoder.yudao.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.system.framework.datapermission.config.DataPermissionConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApiImpl;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.scope.*;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Import({UserOrganizationScopeServiceImpl.class, OrganizationScopeApiImpl.class,
        YudaoDataPermissionAutoConfiguration.class,
        UserOrganizationScopeServiceImplTest.TenantConfiguration.class})
@Sql(statements = {"DELETE FROM system_user_company_department_scope", "DELETE FROM system_company",
        "DELETE FROM system_users", "DELETE FROM system_dept"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserOrganizationScopeServiceImplTest extends BaseDbUnitTest {
    @Resource private UserOrganizationScopeService service;
    @Resource private OrganizationScopeApi scopeApi;
    @Resource private UserCompanyDepartmentScopeMapper scopeMapper;
    @Resource private AdminUserMapper userMapper;
    @Resource private CompanyMapper companyMapper;
    @Resource private DeptMapper deptMapper;
    @Resource private JdbcTemplate jdbc;
    @Resource private TenantLineInnerInterceptor tenantLineInnerInterceptor;
    @Resource private DataPermissionRuleHandler dataPermissionRuleHandler;

    @BeforeEach
    void seed() {
        TenantContextHolder.setTenantId(1L);
        userMapper.insert(new AdminUserDO().setId(1101L).setUsername("scope-user")
                .setPassword("").setNickname("范围用户").setStatus(0));
        companyMapper.insert(new CompanyDO().setId(2101L).setCode("SCOPE-COMPANY").setName("范围公司")
                .setStatus(0).setVersion(0));
        deptMapper.insert(new DeptDO().setId(3101L).setCode("SCOPE-DEPT").setName("范围部门")
                .setParentId(0L).setSort(0).setStatus(0).setVersion(0));
    }

    @AfterEach
    void clearContext() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test
    void createDerivesOrganizationFactsAndFeedsExistingAuthorization() {
        Long id = service.create(request());
        UserCompanyDepartmentScopeDO scope = scopeMapper.selectById(id);
        assertEquals("SCOPE-COMPANY", scope.getCompanyCode());
        assertEquals("SCOPE-DEPT", scope.getDepartmentCode());
        assertEquals(1L, scope.getTenantId());
        assertEquals(0, scope.getVersion());
        assertTrue(scopeApi.hasScope(1101L, 2101L, 3101L));
    }

    @Test
    void companyScopeHasNoDepartmentPairAndMultipleRolesAreRetained() {
        var companyScope = request(); companyScope.setDepartmentId(null);
        Long id = service.create(companyScope);
        assertNull(scopeMapper.selectById(id).getDepartmentId());
        assertNull(scopeMapper.selectById(id).getDepartmentCode());
        var serviceManager = request(); serviceManager.setScopeRole("SERVICE_MANAGER");
        service.create(serviceManager);
        assertEquals(2, scopeApi.getActiveScopes(1101L).size());
    }

    @Test
    void reviseAppendsAndPreservesOriginalBusinessFields() {
        Long oldId = service.create(request());
        var original = scopeMapper.selectById(oldId);
        var update = request(); update.setExpectedVersion(0); update.setDepartmentId(null);
        update.setScopeRole("SERVICE_MANAGER"); update.setIsPrimary(false);
        Long newId = service.revise(oldId, update);
        assertNotEquals(oldId, newId);
        var old = scopeMapper.selectById(oldId);
        assertEquals(1, old.getStatus()); assertEquals(1, old.getVersion());
        assertEquals(original.getDepartmentId(), old.getDepartmentId());
        assertEquals(original.getScopeRole(), old.getScopeRole());
        assertEquals(original.getIsPrimary(), old.getIsPrimary());
        assertEquals(original.getEffectiveFrom(), old.getEffectiveFrom());
        assertEquals(1, scopeApi.getActiveScopes(1101L).size());
        assertEquals(newId, scopeApi.getActiveScopes(1101L).getFirst().getId());
        assertEquals(2, service.getPage(page()).getTotal());
    }

    @Test
    void staleRevisionAndRepeatedDisableRejectWithoutExtraRows() {
        Long id = service.create(request());
        var update = request(); update.setExpectedVersion(9);
        assertServiceException(() -> service.revise(id, update), USER_ORGANIZATION_SCOPE_VERSION_CONFLICT);
        service.disable(id, disableRequest());
        assertServiceException(() -> service.disable(id, disableRequest()), USER_ORGANIZATION_SCOPE_VERSION_CONFLICT);
        assertServiceException(() -> service.revise(id, request()), USER_ORGANIZATION_SCOPE_VERSION_CONFLICT);
        assertEquals(1, service.getPage(page()).getTotal());
        assertTrue(scopeApi.getActiveScopes(1101L).isEmpty());
    }

    @Test
    void failedInsertRollsBackOriginalDisable() {
        Long id = service.create(request());
        jdbc.execute("ALTER TABLE system_user_company_department_scope ADD CONSTRAINT test_no_service_manager CHECK (scope_role <> 'SERVICE_MANAGER')");
        try {
            var update = request(); update.setExpectedVersion(0); update.setScopeRole("SERVICE_MANAGER");
            assertThrows(Exception.class, () -> service.revise(id, update));
            assertEquals(0, scopeMapper.selectById(id).getStatus());
            assertEquals(0, scopeMapper.selectById(id).getVersion());
            assertEquals(1, service.getPage(page()).getTotal());
        } finally {
            jdbc.execute("ALTER TABLE system_user_company_department_scope DROP CONSTRAINT test_no_service_manager");
        }
    }

    @Test
    void intervalFailureDoesNotDisableOriginal() {
        Long id = service.create(request());
        var update = request(); update.setExpectedVersion(0); update.setEffectiveTo(update.getEffectiveFrom());
        assertServiceException(() -> service.revise(id, update), USER_ORGANIZATION_SCOPE_INTERVAL_INVALID);
        assertEquals(0, scopeMapper.selectById(id).getStatus());
    }

    @Test
    void invalidCompanyAndDepartmentRejectNewAuthorization() {
        var invalid = request(); invalid.setCompanyId(9999L);
        assertServiceException(() -> service.create(invalid), ORGANIZATION_SCOPE_INVALID);
        invalid.setCompanyId(2101L); invalid.setDepartmentId(9999L);
        assertServiceException(() -> service.create(invalid), ORGANIZATION_SCOPE_INVALID);
        deptMapper.updateById(new DeptDO().setId(3101L).setStatus(1));
        assertServiceException(() -> service.create(request()), ORGANIZATION_SCOPE_INVALID);
        assertEquals(0, service.getPage(page()).getTotal());
    }

    @Test
    void disabledUserCannotReceiveNewScopesButCanHaveScopesRevoked() {
        Long id = service.create(request());
        userMapper.updateById(new AdminUserDO().setId(1101L).setStatus(1));
        companyMapper.updateById(new CompanyDO().setId(2101L).setStatus(1));
        deptMapper.updateById(new DeptDO().setId(3101L).setStatus(1));
        assertServiceException(() -> service.create(request()), USER_IS_DISABLE, "范围用户");
        service.disable(id, disableRequest());
        assertEquals(1, scopeMapper.selectById(id).getStatus());
        assertTrue(scopeApi.getActiveScopes(1101L).isEmpty());
    }

    @Test
    void futureAndExpiredScopesAreListedButDoNotAuthorize() {
        var future = request(); future.setEffectiveFrom(LocalDateTime.now().plusDays(1)); service.create(future);
        var expired = request(); expired.setEffectiveTo(LocalDateTime.now().minusHours(1)); service.create(expired);
        assertEquals(2, service.getPage(page()).getTotal());
        assertTrue(scopeApi.getActiveScopes(1101L).isEmpty());
    }

    @Test
    void userBindingCannotBeChangedThroughForeignScopeId() {
        Long id = service.create(request());
        userMapper.insert(new AdminUserDO().setId(1102L).setUsername("other-user").setPassword("").setNickname("其他用户").setStatus(0));
        var disable = disableRequest(); disable.setUserId(1102L);
        assertServiceException(() -> service.disable(id, disable), USER_ORGANIZATION_SCOPE_NOT_FOUND);
        var update = request(); update.setUserId(1102L); update.setExpectedVersion(0);
        assertServiceException(() -> service.revise(id, update), USER_ORGANIZATION_SCOPE_NOT_FOUND);
        assertEquals(0, scopeMapper.selectById(id).getStatus());
    }

    @Test
    void frameworkTenantIsolationRejectsReadCreateReviseAndDisable() {
        Long id = service.create(request());
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> service.getPage(page()), USER_NOT_EXISTS);
        assertServiceException(() -> service.create(request()), USER_NOT_EXISTS);
        assertServiceException(() -> service.revise(id, request()), USER_NOT_EXISTS);
        assertServiceException(() -> service.disable(id, disableRequest()), USER_NOT_EXISTS);
        assertNull(scopeMapper.selectById(id));
        TenantContextHolder.setTenantId(1L);
        assertEquals(0, scopeMapper.selectById(id).getStatus());
    }

    @Test
    void pageFiltersStatusWithoutChangingAuthorizationAndKeepsStableOrder() {
        Long first = service.create(request()); Long second = service.create(request());
        service.disable(first, disableRequest());
        var page = page(); page.setPageSize(1);
        assertEquals(second, service.getPage(page).getList().getFirst().getId());
        assertEquals(2, service.getPage(page).getTotal());
        page.setStatus(1);
        assertEquals(first, service.getPage(page).getList().getFirst().getId());
        assertEquals(1, scopeApi.getActiveScopes(1101L).size());
    }

    @Test
    void existingSystemUserDataScopeRejectsMaintenanceOutsideVisibleUsers() {
        Long id = service.create(request());
        LoginUser actor = new LoginUser().setId(999L).setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        assertServiceException(() -> service.getPage(page()), USER_NOT_EXISTS);
        assertServiceException(() -> service.create(request()), USER_NOT_EXISTS);
        assertServiceException(() -> service.revise(id, request()), USER_NOT_EXISTS);
        assertServiceException(() -> service.disable(id, disableRequest()), USER_NOT_EXISTS);
        SecurityContextHolder.clearContext();
        assertEquals(0, scopeMapper.selectById(id).getStatus());
    }

    private static UserOrganizationScopeSaveReqVO request() {
        var req = new UserOrganizationScopeSaveReqVO();
        req.setUserId(1101L); req.setCompanyId(2101L); req.setDepartmentId(3101L);
        req.setScopeRole("PROJECT_MANAGER"); req.setIsPrimary(true);
        req.setEffectiveFrom(LocalDateTime.now().minusDays(1).withNano(0));
        return req;
    }
    private static UserOrganizationScopePageReqVO page() {
        var req = new UserOrganizationScopePageReqVO(); req.setUserId(1101L); return req;
    }
    private static UserOrganizationScopeDisableReqVO disableRequest() {
        var req = new UserOrganizationScopeDisableReqVO(); req.setUserId(1101L); req.setExpectedVersion(0); return req;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TenantConfiguration {
        @Bean
        JdbcTemplate jdbcTemplate(javax.sql.DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean
        DeptDataPermissionRule deptDataPermissionRule() {
            PermissionCommonApi permissions = mock(PermissionCommonApi.class);
            when(permissions.getDeptDataPermission(anyLong())).thenReturn(new DeptDataPermissionRespDTO());
            var rule = new DeptDataPermissionRule(permissions);
            new DataPermissionConfiguration().sysDeptDataPermissionRuleCustomizer().customize(rule);
            return rule;
        }
        @Bean
        TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            var inner = new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
