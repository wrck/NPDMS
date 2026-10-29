package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectBoundCustomerQueryApiTest {
    private final CustomerMasterMapper mapper = mock(CustomerMasterMapper.class);
    private final CustomerQueryService directory = mock(CustomerQueryService.class);
    private final CustomerScopeContextService directoryScopes = mock(CustomerScopeContextService.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectBusinessContextApi projects = mock(ProjectBusinessContextApi.class);
    private final cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi scopes = mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
    private final CustomerQueryApiImpl api = new CustomerQueryApiImpl(mapper, directory, directoryScopes, permissions);
    private final CustomerQueryApi.ProjectQuery query = new CustomerQueryApi.ProjectQuery(1L, 3L, 7L);
    private final ProjectBusinessContextApi.Query projectQuery = new ProjectBusinessContextApi.Query(1L, 3L, 7L, 2L);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        ReflectionTestUtils.setField(api, "projectContexts", projects);
        ReflectionTestUtils.setField(api, "projectScopes", scopes);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(3L).setTenantId(1L),
                new org.springframework.mock.web.MockHttpServletRequest());
        when(scopes.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(7L, 2L, java.util.Set.of(7L), java.util.Set.of()));
        when(permissions.hasAnyPermissions(3L, "pms:customer:query")).thenReturn(true);
        when(projects.inspect(projectQuery)).thenReturn(project(null, "CUS-8"));
    }

    @AfterEach void clear() { TenantContextHolder.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Test void resolvesOnlyTheOwnerBoundCodeWithoutCustomerDirectoryScope() {
        when(mapper.selectByTenantIdAndCode(1L, "CUS-8")).thenReturn(customer(1L, "ENABLED"));
        assertEquals(8L, api.getProjectCustomer(query).customer().id());
        verify(projects).inspect(projectQuery);
        verify(mapper).selectByTenantIdAndCode(1L, "CUS-8");
        verifyNoInteractions(directory, directoryScopes);
        verify(mapper, never()).updateById(any(CustomerMasterDO.class));
    }

    @Test void currentProjectDenialStopsBeforeCustomerLookup() {
        when(projects.inspect(projectQuery)).thenThrow(new IllegalStateException("scope revoked"));
        assertThrows(IllegalStateException.class, () -> api.getProjectCustomer(query));
        verifyNoInteractions(mapper, directory, directoryScopes);
    }

    @Test void missingQueryPermissionCannotUseProjectBinding() {
        when(permissions.hasAnyPermissions(3L, "pms:customer:query")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        verifyNoInteractions(projects, mapper, directory, directoryScopes);
    }

    @Test void crossTenantRequestStopsBeforeOwnerOrCustomerReads() {
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(new CustomerQueryApi.ProjectQuery(2L, 3L, 7L)));
        verifyNoInteractions(projects, mapper, directory, directoryScopes);
    }

    @Test void inconsistentOwnerProjectFailsClosed() {
        when(projects.inspect(projectQuery)).thenReturn(ProjectBusinessContextApi.Context.builder()
                .tenantId(1L).projectId(99L).customerCode("CUS-8").build());
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        verifyNoInteractions(mapper, directory, directoryScopes);
    }

    @Test void missingScopeVersionOrActorStopsBeforeOwnerOrCustomerReads() {
        when(scopes.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(7L, null, java.util.Set.of(7L), java.util.Set.of()));
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(new CustomerQueryApi.ProjectQuery(1L, null, 7L)));
        verifyNoInteractions(projects, mapper, directory, directoryScopes);
    }

    @Test void inconsistentOwnerTenantFailsClosed() {
        when(projects.inspect(projectQuery)).thenReturn(ProjectBusinessContextApi.Context.builder()
                .tenantId(2L).projectId(7L).customerCode("CUS-8").build());
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        verifyNoInteractions(mapper, directory, directoryScopes);
    }

    @Test void trulyUnassociatedProjectDoesNotReadGlobalCustomers() {
        when(projects.inspect(projectQuery)).thenReturn(project(null, null));
        assertNull(api.getProjectCustomer(query).customer());
        verifyNoInteractions(mapper, directory, directoryScopes);
    }

    @Test void summaryOnlyProjectCannotReachBoundCustomer() {
        when(scopes.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(
                7L, 2L, java.util.Set.of(), java.util.Set.of(7L)));
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        verifyNoInteractions(projects, mapper, directory, directoryScopes);
    }

    @Test void forgedActorOrUnauthenticatedRequestCannotReachOwner() {
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(new CustomerQueryApi.ProjectQuery(1L, 99L, 7L)));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        verifyNoInteractions(scopes, projects, mapper, directory, directoryScopes);
    }

    @Test void loginTenantMismatchCannotBorrowCurrentTenant() {
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(3L).setTenantId(2L),
                new org.springframework.mock.web.MockHttpServletRequest());
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        verifyNoInteractions(scopes, projects, mapper, directory, directoryScopes);
    }

    @Test void inconsistentScopeVersionFromOwnerFailsClosed() {
        when(projects.inspect(projectQuery)).thenReturn(ProjectBusinessContextApi.Context.builder()
                .tenantId(1L).projectId(7L).scopeVersion(3L).customerCode("CUS-8").build());
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        verifyNoInteractions(mapper, directory, directoryScopes);
    }

    @Test void storedIdTakesPriorityAndMissingIdDoesNotFallBackToCode() {
        when(projects.inspect(projectQuery)).thenReturn(project(8L, "OTHER-CODE"));
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        verify(mapper).selectById(8L);
        verify(mapper, never()).selectByTenantIdAndCode(any(), any());
    }

    @Test void missingDeletedOrForeignCustomerCannotBecomeAProjectBinding() {
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        when(mapper.selectByTenantIdAndCode(1L, "CUS-8")).thenReturn(customer(1L, "DELETED"));
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        when(mapper.selectByTenantIdAndCode(1L, "CUS-8")).thenReturn(customer(2L, "ENABLED"));
        assertThrows(RuntimeException.class, () -> api.getProjectCustomer(query));
        verifyNoInteractions(directory, directoryScopes);
    }

    @Test void disabledExistingCustomerRemainsReadableWithoutCreatingANewRelation() {
        when(mapper.selectByTenantIdAndCode(1L, "CUS-8")).thenReturn(customer(1L, "DISABLED"));
        assertEquals("DISABLED", api.getProjectCustomer(query).customer().lifecycleStatus());
        verify(mapper, never()).updateById(any(CustomerMasterDO.class));
    }

    private ProjectBusinessContextApi.Context project(Long id, String code) {
        return ProjectBusinessContextApi.Context.builder().tenantId(1L).projectId(7L)
                .scopeVersion(2L).customerId(id).customerCode(code).build();
    }

    private CustomerMasterDO customer(Long tenantId, String status) {
        var customer = new CustomerMasterDO(); customer.setId(8L); customer.setTenantId(tenantId);
        customer.setCode("CUS-8"); customer.setName("客户"); customer.setLifecycleStatus(status);
        return customer;
    }
}
