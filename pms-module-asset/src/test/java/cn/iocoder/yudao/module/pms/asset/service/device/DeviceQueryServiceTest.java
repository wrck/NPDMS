package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DevicePageReadScope;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceQueryServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceAccessScopeService accessScopeService;
    @Mock private cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi projects;
    @Mock private cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi customers;
    private DeviceQueryService service;

    private DevicePageReadScope readScope() {
        return new DevicePageReadScope(
                new ProjectViewReadScopeApi.ReadScope(1L, 7L, ProjectViewReadScopeApi.Mode.TENANT_ADMIN,
                        LocalDateTime.now(), List.of()),
                new ContractDeviceVisibilityApi.ReadScope(List.of(new ContractDeviceVisibilityApi.OrganizationGrant(88L, 99L, "D99"))));
    }

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser();
        user.setId(7L);
        user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
        service = new DeviceQueryService(deviceMapper, accessScopeService, projects, customers);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void projectVisibilityLoadsWholeDeviceRangeWithoutRepeatedVisibilityQueries() {
        var query = new ProjectDeviceInstallationQuery(1L, 10L);
        var first = device(8L); var second = device(9L);
        when(accessScopeService.hasFullProjectView(any())).thenReturn(true);
        when(deviceMapper.selectCurrentProjectDevices(query)).thenReturn(List.of(first, second));

        assertEquals(List.of(first, second), service.getProjectDevices(query));

        verify(accessScopeService).hasFullProjectView(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery(1L, 7L, 10L, "PROJECT_VIEW"));
        verifyNoMoreInteractions(accessScopeService);
        verify(deviceMapper).selectCurrentProjectDevices(query);
        verifyNoMoreInteractions(deviceMapper);
    }

    @Test
    void projectBatchRejectsForeignTenantBeforeReadingAnyScopeOrDevice() {
        assertThrows(SecurityException.class,
                () -> service.getProjectDevices(new ProjectDeviceInstallationQuery(2L, 10L)));
        verifyNoInteractions(accessScopeService, deviceMapper);
    }

    @Test
    void withoutProjectVisibilityBatchStillUsesOriginalOrganizationAuthorization() {
        var query = new ProjectDeviceInstallationQuery(1L, 10L);
        var first = device(8L); var second = device(9L);
        var grant = new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant(20L, null, null);
        when(accessScopeService.organizationGrants(1L, 7L)).thenReturn(List.of(grant));
        when(accessScopeService.pageReadScope(any())).thenReturn(new DevicePageReadScope(null, null));
        when(deviceMapper.selectCurrentProjectDevices(query)).thenReturn(List.of(first, second));
        when(deviceMapper.selectJoinedDeviceIds(any())).thenReturn(List.of(first, second));
        assertEquals(List.of(first, second), service.getProjectDevices(query));
        verify(deviceMapper).selectJoinedDeviceIds(argThat(q -> q.deviceIds().equals(Set.of(8L, 9L))
                && q.organizationGrants().equals(List.of(grant))));
        verify(accessScopeService, never()).assertVisible(anyLong(), anyLong(), anyLong());
        verify(deviceMapper, never()).selectById(anyLong());
    }

    @Test
    void aDeniedDeviceFailsWholeBatchInsteadOfReturningAPartialRange() {
        var query = new ProjectDeviceInstallationQuery(1L, 10L);
        when(deviceMapper.selectCurrentProjectDevices(query)).thenReturn(List.of(device(8L), device(9L)));
        when(deviceMapper.selectJoinedDeviceIds(argThat(q -> q != null && q.deviceIds().equals(Set.of(8L, 9L)))))
                .thenReturn(List.of(device(8L)));
        when(deviceMapper.selectJoinedDeviceIds(argThat(q -> q != null && q.deviceIds().equals(Set.of(9L)))))
                .thenReturn(List.of());
        var failure = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.getProjectDevices(query));
        assertEquals(cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS.getCode(), failure.getCode());
        verify(deviceMapper, never()).selectById(anyLong());
    }

    private static DeviceDO device(Long id) {
        var row = new DeviceDO(); row.setId(id); row.setTenantId(1L); row.setProjectId(10L); return row;
    }

    @Test
    void shouldUseAuthenticatedTenantForPageWhenTenantContextIsDisabled() {
        VisibleDevicePageQuery request = VisibleDevicePageQuery.builder()
                .sn("SN-8")
                .pageNo(1)
                .pageSize(20).build();
        var readScope = readScope();
        VisibleDevicePageQuery query = VisibleDevicePageQuery.builder()
                .tenantId(1L)
                .sn("SN-8")
                .pageNo(1)
                .pageSize(20)
                .organizationGrants(List.of())
                .readScope(readScope).build();
        PageResult<DeviceListProjection> expected = new PageResult<>(List.of(), 1L);
        when(accessScopeService.pageReadScope(new ProjectAllScopeQuery(1L, 7L, "PROJECT_VIEW"))).thenReturn(readScope);
        when(deviceMapper.selectJoinedDevicePage(query)).thenReturn(expected);

        assertSame(expected, service.getPage(request));
        verify(deviceMapper).selectJoinedDevicePage(query);
        verify(accessScopeService, never()).visibleProjectIds(1L, 7L);
        verify(deviceMapper, never()).selectVisibleDevicePage(any());
    }

    @Test
    void emptyNameMatchDoesNotBecomeAnUnfilteredDeviceQuery() {
        when(projects.findProjectIdsByName(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of());
        var page = service.getPage(VisibleDevicePageQuery.builder().projectName("missing").pageNo(1).pageSize(10).build());
        assertEquals(0L, page.getTotal());
        org.mockito.Mockito.verifyNoInteractions(deviceMapper, accessScopeService);
    }

    @Test
    void nameMatchesRemainFiltersAlongsideTheOwnerReadScope() {
        when(projects.findProjectIdsByName(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of(10L, 20L));
        when(customers.findCustomerIdsByName(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of(30L));
        var readScope = readScope();
        when(accessScopeService.pageReadScope(new ProjectAllScopeQuery(1L, 7L, "PROJECT_VIEW"))).thenReturn(readScope);
        when(deviceMapper.selectJoinedDevicePage(org.mockito.ArgumentMatchers.any())).thenReturn(PageResult.empty());
        service.getPage(VisibleDevicePageQuery.builder().projectName("项目").customerName("客户")
                .departmentName(" 杭州 ").projectId(10L).pageNo(1).pageSize(10).build());
        org.mockito.Mockito.verify(projects).findProjectIdsByName(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(customers).findCustomerIdsByName(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(accessScopeService).pageReadScope(new ProjectAllScopeQuery(1L, 7L, "PROJECT_VIEW"));
        org.mockito.Mockito.verify(deviceMapper).selectJoinedDevicePage(org.mockito.ArgumentMatchers.argThat(q ->
                q.readScope().equals(readScope) && q.visibleProjectIds() == null && q.visibleContractNumbers() == null
                        && q.matchingProjectIds().equals(Set.of(10L, 20L))
                        && q.matchingCustomerIds().equals(Set.of(30L)) && q.projectId().equals(10L)
                        && "杭州".equals(q.departmentName())));
        org.mockito.Mockito.verify(accessScopeService, never()).visibleProjectIds(1L, 7L);
        org.mockito.Mockito.verify(accessScopeService, never()).contractScope(anyLong(), anyLong(), any());
        org.mockito.Mockito.verify(deviceMapper, never()).selectVisibleDevicePage(any());
    }

    @Test
    void fullProjectPageReusesExistingProjectionWithoutPreparingGlobalReadScopes() {
        var projectScope = mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
        var projectReadScope = mock(ProjectViewReadScopeApi.class);
        var organizations = mock(cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi.class);
        var contracts = mock(ContractDeviceVisibilityApi.class);
        var current = new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery(1L, 7L, 10L, "PROJECT_VIEW");
        lenient().when(projectScope.resolveCurrent(current)).thenReturn(
                new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(10L, 4L, Set.of(10L), Set.of()));
        var realAccess = new DeviceAccessScopeService(projectScope, deviceMapper, contracts, organizations, projectReadScope, org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class));
        var currentService = new DeviceQueryService(deviceMapper, realAccess, projects, customers);
        var request = VisibleDevicePageQuery.builder().projectId(10L).sn("SN-8").productCode("PC-1")
                .name("防火墙").status("IN_USE").customerId(20L).departmentName(" 杭州 ")
                .pageNo(2).pageSize(10).build();
        var expectedQuery = request.toBuilder().tenantId(1L).visibleProjectIds(Set.of(10L))
                .departmentName("杭州").organizationGrants(List.of()).build();
        var expected = new PageResult<DeviceListProjection>(List.of(), 21L);
        lenient().when(deviceMapper.selectVisibleDevicePage(expectedQuery)).thenReturn(expected);

        assertSame(expected, currentService.getPage(request));

        verify(projectScope).resolveCurrent(current);
        verify(projectScope, never()).resolveAllCurrent(any());
        verifyNoInteractions(projectReadScope, organizations, contracts);
        verify(deviceMapper).selectVisibleDevicePage(expectedQuery);
        verify(deviceMapper, never()).selectJoinedDevicePage(any());
    }

    @Test
    void nonFullProjectPreservesGlobalProjectContractAndOrganizationFallback() {
        assertNonFullProjectFallback(false);
    }

    @Test
    void failedFullProjectProbePreservesIndependentAuthorizationFallback() {
        assertNonFullProjectFallback(true);
    }

    private void assertNonFullProjectFallback(boolean ownerFails) {
        var projectScope = mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
        var projectReadScope = mock(ProjectViewReadScopeApi.class);
        var organizations = mock(cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi.class);
        var contracts = mock(ContractDeviceVisibilityApi.class);
        if (ownerFails) lenient().when(projectScope.resolveCurrent(any())).thenThrow(new IllegalStateException("owner unavailable"));
        else lenient().when(projectScope.resolveCurrent(any())).thenReturn(
                new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(10L, 4L, Set.of(), Set.of(10L)));
        var readScope = readScope();
        when(projectReadScope.prepare(new ProjectAllScopeQuery(1L, 7L, "PROJECT_VIEW"))).thenReturn(readScope.projectScope());
        when(contracts.prepareReadScope(new ContractDeviceVisibilityApi.ReadScopeQuery(1L, 7L))).thenReturn(readScope.contractScope());
        var realAccess = new DeviceAccessScopeService(projectScope, deviceMapper, contracts, organizations, projectReadScope, org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class));
        var currentService = new DeviceQueryService(deviceMapper, realAccess, projects, customers);
        var expected = new PageResult<DeviceListProjection>(List.of(), 1L);
        when(deviceMapper.selectJoinedDevicePage(any())).thenReturn(expected);

        assertSame(expected, currentService.getPage(VisibleDevicePageQuery.builder().projectId(10L).pageNo(1).pageSize(10).build()));

        verify(deviceMapper).selectJoinedDevicePage(argThat(q -> q.projectId().equals(10L)
                && q.visibleProjectIds() == null && q.readScope().equals(readScope)));
        verify(deviceMapper, never()).selectVisibleDevicePage(any());
        verify(organizations).getActiveScopes(7L);
        verify(contracts).prepareReadScope(new ContractDeviceVisibilityApi.ReadScopeQuery(1L, 7L));
    }

    @Test
    void crossTenantProjectPageCannotProbeOwnerOrReadDevices() {
        var page = service.getPage(VisibleDevicePageQuery.builder().tenantId(2L).projectId(10L).pageNo(1).pageSize(10).build());
        assertEquals(0L, page.getTotal());
        verifyNoInteractions(accessScopeService, deviceMapper);
    }

    @Test void superAdministratorPageRetainsFiltersWithoutRequiringOrganizationOrProjectScope() {
        when(accessScopeService.hasTenantWideRead(new ProjectAllScopeQuery(1L, 7L, "PROJECT_VIEW"))).thenReturn(true);
        when(deviceMapper.selectVisibleDevicePage(any())).thenReturn(PageResult.empty());
        service.getPage(VisibleDevicePageQuery.builder().sn("SN-8").projectId(10L).pageNo(2).pageSize(10).build());
        verify(deviceMapper).selectVisibleDevicePage(argThat(q -> q.tenantWideRead() && q.tenantId().equals(1L)
                && "SN-8".equals(q.sn()) && q.projectId().equals(10L) && q.pageNo()==2 && q.pageSize()==10));
        verify(accessScopeService, never()).organizationGrants(any(), any());
        verify(accessScopeService, never()).pageReadScope(any());
        verify(accessScopeService, never()).hasFullProjectView(any());
    }

    @Test void clientSuppliedTenantWideFlagCannotGrantOrdinaryUserVisibility() {
        when(deviceMapper.selectJoinedDevicePage(any())).thenReturn(PageResult.empty());
        service.getPage(VisibleDevicePageQuery.builder().tenantWideRead(true).pageNo(1).pageSize(10).build());
        verify(deviceMapper).selectJoinedDevicePage(argThat(q -> !q.tenantWideRead()));
        verify(deviceMapper, never()).selectVisibleDevicePage(any());
    }

    @Test void superAdministratorDetailUsesTenantAndExactIdsWithoutOwnerScopeExpansion() {
        when(accessScopeService.hasTenantWideRead(any())).thenReturn(true);
        when(deviceMapper.selectJoinedDeviceIds(any())).thenReturn(List.of(device(8L)));
        assertEquals(8L, service.getDevice(8L).getId());
        verify(deviceMapper).selectJoinedDeviceIds(argThat(q -> q.tenantWideRead() && q.tenantId().equals(1L)
                && q.deviceIds().equals(Set.of(8L))));
        verify(accessScopeService, never()).organizationGrants(any(), any());
        verify(accessScopeService, never()).pageReadScope(any());
    }

    @Test
    void shouldUseAuthenticatedTenantForDetailWhenTenantContextIsDisabled() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        when(accessScopeService.pageReadScope(any())).thenReturn(readScope());
        when(deviceMapper.selectJoinedDeviceIds(any())).thenReturn(List.of(device));

        assertEquals(8L, service.getDevice(8L).getId());
        verify(deviceMapper).selectJoinedDeviceIds(argThat(q -> q.tenantId().equals(1L)
                && q.deviceIds().equals(Set.of(8L)) && q.readScope() != null));
        verify(deviceMapper, never()).selectById(anyLong());
        verify(deviceMapper, never()).existsVisibleDevice(any());
    }

    @Test void batchRejectsForgedActorTenantAndMissingIdentityBeforeAnyRead() {
        var valid = new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(1L, 7L, Set.of(8L), null);
        assertThrows(SecurityException.class, () -> service.getVisibleDevices(
                new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(2L, 7L, Set.of(8L), null)));
        assertThrows(SecurityException.class, () -> service.getVisibleDevices(
                new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(1L, 8L, Set.of(8L), null)));
        assertThrows(SecurityException.class, () -> service.getVisibleDevices(
                new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(1L, 7L, null, null)));
        assertEquals(List.of(), service.getVisibleDevices(
                new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(1L, 7L, Set.of(), null)));
        SecurityContextHolder.clearContext();
        assertThrows(SecurityException.class, () -> service.getVisibleDevices(valid));
        verifyNoInteractions(accessScopeService, deviceMapper);
    }

    @Test void batchWithoutProjectHintDoesNotExpandGlobalProjectIdsOrContractNumbers() {
        service.getVisibleDevices(new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(
                1L, 7L, Set.of(8L, 9L), null));

        verify(accessScopeService, never()).visibleProjectIds(anyLong(), anyLong());
        verify(accessScopeService, never()).contractScope(anyLong(), anyLong(), any());
        verify(accessScopeService).pageReadScope(new ProjectAllScopeQuery(1L, 7L, "PROJECT_VIEW"));
    }

    @Test void provenFullProjectBatchAvoidsAllGlobalOwnerPreparation() {
        var query = new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(1L, 7L, Set.of(8L, 9L), 10L);
        when(accessScopeService.hasFullProjectView(any())).thenReturn(true);
        when(deviceMapper.selectCurrentProjectDevicesByIds(any())).thenReturn(List.of(device(8L), device(9L)));

        assertEquals(2, service.getVisibleDevices(query).size());

        verify(deviceMapper).selectCurrentProjectDevicesByIds(argThat(q -> q.projectId().equals(10L) && q.tenantId().equals(1L) && q.deviceIds().equals(Set.of(8L, 9L))));
        verify(accessScopeService, never()).visibleProjectIds(anyLong(), anyLong());
        verify(accessScopeService, never()).organizationGrants(anyLong(), anyLong());
        verify(accessScopeService, never()).contractScope(anyLong(), anyLong(), any());
    }

    @Test void projectHintCannotDropOtherAuthorizedDeviceOrReplaceGlobalFallback() {
        var query = new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(1L, 7L, Set.of(8L, 9L), 10L);
        var first = device(8L); var other = device(9L); other.setProjectId(20L);
        when(accessScopeService.hasFullProjectView(any())).thenReturn(true);
        when(accessScopeService.pageReadScope(any())).thenReturn(readScope());
        when(deviceMapper.selectCurrentProjectDevicesByIds(argThat(q -> q != null && q.projectId() != null))).thenReturn(List.of(first));
        when(deviceMapper.selectJoinedDeviceIds(any())).thenReturn(List.of(other));

        assertEquals(List.of(first, other), service.getVisibleDevices(query));

        verify(accessScopeService).pageReadScope(new ProjectAllScopeQuery(1L, 7L, "PROJECT_VIEW"));
        verify(deviceMapper).selectJoinedDeviceIds(argThat(q -> q.deviceIds().equals(Set.of(9L)) && q.projectId() == null
                && q.readScope() != null));
    }

    @Test void projectHintWithoutFullViewCannotGrantProjectDeviceAccess() {
        var query = new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(1L, 7L, Set.of(8L), 10L);
        when(accessScopeService.hasFullProjectView(any())).thenReturn(false);

        assertEquals(List.of(), service.getVisibleDevices(query));

        verify(deviceMapper, never()).selectCurrentProjectDevicesByIds(any());
        verify(deviceMapper).selectJoinedDeviceIds(argThat(q -> q.deviceIds().equals(Set.of(8L)) && q.projectId() == null));
    }

    @Test void missingSingleDeviceRetainsOriginalNotVisibleErrorWithoutExistsOrBackRead() {
        var failure = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.getDevice(8L));
        assertEquals(cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS.getCode(), failure.getCode());
        verify(deviceMapper, never()).existsVisibleDevice(any());
        verify(deviceMapper, never()).selectById(anyLong());
    }
}
