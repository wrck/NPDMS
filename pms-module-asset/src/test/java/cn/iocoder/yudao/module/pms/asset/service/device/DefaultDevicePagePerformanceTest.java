package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceContractCandidateQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultDevicePagePerformanceTest {
    final DeviceMapper devices = mock(DeviceMapper.class);
    final ProjectViewReadScopeApi viewScopes = mock(ProjectViewReadScopeApi.class);
    final ProjectScopeApi projects = mock(ProjectScopeApi.class);
    final ContractDeviceVisibilityApi contracts = mock(ContractDeviceVisibilityApi.class);
    final OrganizationScopeApi organizations = mock(OrganizationScopeApi.class);
    final DeviceAccessScopeService access = new DeviceAccessScopeService(projects, devices, contracts, organizations, viewScopes);

    @BeforeEach void login() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(7L); user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }

    @AfterEach void cleanup() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private ProjectViewReadScopeApi.ReadScope tenantAdminReadScope() {
        return new ProjectViewReadScopeApi.ReadScope(1L, 7L, ProjectViewReadScopeApi.Mode.TENANT_ADMIN,
                LocalDateTime.now(), List.of());
    }

    @Test void defaultPageUsesOwnerReadScopesWithoutEnumeratingProjectOrContractIds() {
        when(viewScopes.prepare(any())).thenReturn(tenantAdminReadScope());
        when(contracts.prepareReadScope(any())).thenReturn(new ContractDeviceVisibilityApi.ReadScope(List.of(new ContractDeviceVisibilityApi.OrganizationGrant(88L, 99L, "D99"))));
        when(devices.selectJoinedDevicePage(any())).thenReturn(PageResult.empty());

        assertEquals(0L, new DeviceQueryService(devices, access, null, null).getPage(
                VisibleDevicePageQuery.builder().pageNo(1).pageSize(10).build()).getTotal());

        var query = ArgumentCaptor.forClass(VisibleDevicePageQuery.class);
        verify(devices).selectJoinedDevicePage(query.capture());
        assertEquals(1L, query.getValue().tenantId());
        assertNull(query.getValue().visibleProjectIds());
        assertNull(query.getValue().visibleContractNumbers());
        assertTrue(query.getValue().readScope().projectScope().trees().isEmpty());
        assertEquals(List.of(new ContractDeviceVisibilityApi.OrganizationGrant(88L, 99L, "D99")), query.getValue().readScope().contractScope().organizationGrants());
        verify(viewScopes).prepare(any());
        verify(contracts).prepareReadScope(any());
        verify(projects, never()).resolveAllCurrent(any());
        verify(contracts, never()).getVisibleContractNumbers(anyLong(), anyLong());
        verify(contracts, never()).getVisibleContractNumbers(anyLong(), anyLong(), anySet());
        verify(devices, never()).selectProjectCandidates(any());
        verify(devices, never()).selectContractCandidates(any());
        verify(devices, never()).selectVisibleDevicePage(any());
    }

    @Test void defaultContractScopeUtilityDoesNotEnumerateDeviceContracts() {
        when(contracts.getVisibleContractNumbers(1L, 7L)).thenReturn(Set.of("C-1", "NO_DEVICE"));
        when(devices.selectContractCandidates(any())).thenThrow(new IllegalStateException("redundant tenant-wide scan"));

        assertEquals(Set.of("C-1", "NO_DEVICE"), access.contractScope(1L, 7L,
                DeviceContractCandidateQuery.builder().tenantId(1L).build()));

        verify(contracts).getVisibleContractNumbers(1L, 7L);
        verifyNoInteractions(devices);
        verify(contracts, never()).getVisibleContractNumbers(anyLong(), anyLong(), anySet());
    }

    @Test void noAuthorizedContractsDoesNotReadDeviceContractCandidates() {
        when(contracts.getVisibleContractNumbers(1L, 7L)).thenReturn(Set.of());

        assertTrue(access.contractScope(1L, 7L,
                DeviceContractCandidateQuery.builder().tenantId(1L).build()).isEmpty());

        verify(devices, never()).selectContractCandidates(any());
    }

    @Test void preciseSerialLookupKeepsItsSmallCandidateFirstPath() {
        var query = DeviceContractCandidateQuery.builder().tenantId(1L).sn("SN-1").build();
        when(devices.selectContractCandidates(query)).thenReturn(Set.of("C-1"));
        when(contracts.getVisibleContractNumbers(1L, 7L, Set.of("C-1"))).thenReturn(Set.of("C-1"));

        assertEquals(Set.of("C-1"), access.contractScope(1L, 7L, query));

        verify(contracts, never()).getVisibleContractNumbers(anyLong(), anyLong());
    }

    @Test void joinedPageRetainsCompanyDepartmentGrantAndBothOwnerScopes() {
        var projectScope = new ProjectViewReadScopeApi.ReadScope(1L, 7L, ProjectViewReadScopeApi.Mode.SCOPED,
                LocalDateTime.now(), List.of(new ProjectViewReadScopeApi.Tree(10L, 3L)));
        when(viewScopes.prepare(any())).thenReturn(projectScope);
        when(contracts.prepareReadScope(any())).thenReturn(new ContractDeviceVisibilityApi.ReadScope(List.of(new ContractDeviceVisibilityApi.OrganizationGrant(88L, 99L, "D99"))));
        var organization = new cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO();
        organization.setId(1L); organization.setVersion(1); organization.setCompanyId(30L);
        organization.setDepartmentId(31L); organization.setDepartmentCode("OFFICE");
        when(organizations.getActiveScopes(7L)).thenReturn(List.of(organization));
        when(devices.selectJoinedDevicePage(any())).thenReturn(PageResult.empty());

        new DeviceQueryService(devices, access, null, null).getPage(
                VisibleDevicePageQuery.builder().pageNo(1).pageSize(10).build());

        var query = ArgumentCaptor.forClass(VisibleDevicePageQuery.class);
        verify(devices).selectJoinedDevicePage(query.capture());
        assertEquals(projectScope, query.getValue().readScope().projectScope());
        assertEquals(List.of(new ContractDeviceVisibilityApi.OrganizationGrant(88L, 99L, "D99")), query.getValue().readScope().contractScope().organizationGrants());
        var grant = query.getValue().organizationGrants().getFirst();
        assertEquals(30L, grant.companyId());
        assertEquals(31L, grant.departmentId());
        assertEquals("OFFICE", grant.departmentCode());
        verify(devices, never()).selectProjectCandidates(any());
        verify(devices, never()).selectContractCandidates(any());
        verify(projects, never()).resolveAllCurrent(any());
        verify(contracts, never()).getVisibleContractNumbers(anyLong(), anyLong());
    }

    @Test void projectOwnerFailureClosesOnlyProjectScopeBranch() {
        when(viewScopes.prepare(any())).thenThrow(new IllegalStateException("project owner unavailable"));
        when(contracts.prepareReadScope(any())).thenReturn(new ContractDeviceVisibilityApi.ReadScope(List.of(new ContractDeviceVisibilityApi.OrganizationGrant(88L, 99L, "D99"))));
        when(devices.selectJoinedDevicePage(any())).thenReturn(PageResult.empty());

        new DeviceQueryService(devices, access, null, null).getPage(
                VisibleDevicePageQuery.builder().pageNo(1).pageSize(10).build());

        var query = ArgumentCaptor.forClass(VisibleDevicePageQuery.class);
        verify(devices).selectJoinedDevicePage(query.capture());
        assertNull(query.getValue().readScope().projectScope());
        assertEquals(List.of(new ContractDeviceVisibilityApi.OrganizationGrant(88L, 99L, "D99")), query.getValue().readScope().contractScope().organizationGrants());
        verify(contracts, never()).getVisibleContractNumbers(anyLong(), anyLong());
    }

    @Test void contractOwnerFailureClosesOnlyContractCompanyScopeBranch() {
        when(viewScopes.prepare(any())).thenReturn(tenantAdminReadScope());
        when(contracts.prepareReadScope(any())).thenThrow(new IllegalStateException("contract owner unavailable"));
        when(devices.selectJoinedDevicePage(any())).thenReturn(PageResult.empty());

        new DeviceQueryService(devices, access, null, null).getPage(
                VisibleDevicePageQuery.builder().pageNo(1).pageSize(10).build());

        var query = ArgumentCaptor.forClass(VisibleDevicePageQuery.class);
        verify(devices).selectJoinedDevicePage(query.capture());
        assertEquals(tenantAdminReadScope().mode(), query.getValue().readScope().projectScope().mode());
        assertNull(query.getValue().readScope().contractScope());
        verify(contracts, never()).getVisibleContractNumbers(anyLong(), anyLong());
    }

    @Test void foreignTenantDoesNotReadAnyNewCandidatesOrOwnerScope() {
        assertEquals(0L, new DeviceQueryService(devices, access, null, null).getPage(
                VisibleDevicePageQuery.builder().tenantId(2L).pageNo(1).pageSize(10).build()).getTotal());
        verifyNoInteractions(devices, contracts, projects, organizations, viewScopes);
    }
}
