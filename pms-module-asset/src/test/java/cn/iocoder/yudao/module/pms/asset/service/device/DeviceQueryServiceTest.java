package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
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
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of(10L));
        when(deviceMapper.selectCurrentProjectDevices(query)).thenReturn(List.of(first, second));

        assertEquals(List.of(first, second), service.getProjectDevices(query));

        verify(accessScopeService).visibleProjectIds(1L, 7L);
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
    void withoutProjectVisibilityEveryDeviceStillUsesOriginalAuthorization() {
        var query = new ProjectDeviceInstallationQuery(1L, 10L);
        var first = device(8L); var second = device(9L);
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of());
        when(deviceMapper.selectCurrentProjectDevices(query)).thenReturn(List.of(first, second));
        when(deviceMapper.selectById(8L)).thenReturn(first);
        when(deviceMapper.selectById(9L)).thenReturn(second);
        assertEquals(List.of(first, second), service.getProjectDevices(query));
        verify(accessScopeService).assertVisible(1L, 7L, 8L);
        verify(accessScopeService).assertVisible(1L, 7L, 9L);
    }

    @Test
    void aDeniedDeviceFailsWholeBatchInsteadOfReturningAPartialRange() {
        var query = new ProjectDeviceInstallationQuery(1L, 10L);
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of());
        when(deviceMapper.selectCurrentProjectDevices(query)).thenReturn(List.of(device(8L), device(9L)));
        when(deviceMapper.selectById(8L)).thenReturn(device(8L));
        doNothing().when(accessScopeService).assertVisible(1L, 7L, 8L);
        doThrow(new SecurityException("denied")).when(accessScopeService).assertVisible(1L, 7L, 9L);
        assertThrows(SecurityException.class, () -> service.getProjectDevices(query));
        verify(deviceMapper, never()).selectById(9L);
    }

    private static DeviceDO device(Long id) {
        var row = new DeviceDO(); row.setId(id); row.setTenantId(1L); row.setProjectId(10L); return row;
    }

    @Test
    void shouldUseAuthenticatedTenantForPageWhenTenantContextIsDisabled() {
        VisibleDevicePageQuery request = VisibleDevicePageQuery.builder()
                .sn("SN-8")
                .pageNo(1)
                .pageSize(20)
                .visibleContractNumbers(Set.of())
                .organizationGrants(java.util.List.of()).build();
        VisibleDevicePageQuery query = VisibleDevicePageQuery.builder()
                .tenantId(1L)
                .visibleProjectIds(Set.of(10L))
                .sn("SN-8")
                .pageNo(1)
                .pageSize(20)
                .visibleContractNumbers(Set.of())
                .organizationGrants(java.util.List.of()).build();
        PageResult<DeviceListProjection> expected = new PageResult<>(List.of(), 1L);
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of(10L));
        when(deviceMapper.selectVisibleDevicePage(query)).thenReturn(expected);

        assertSame(expected, service.getPage(request));
    }

    @Test
    void emptyNameMatchDoesNotBecomeAnUnfilteredDeviceQuery() {
        when(projects.findProjectIdsByName(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of());
        var page = service.getPage(VisibleDevicePageQuery.builder().projectName("missing").pageNo(1).pageSize(10).build());
        assertEquals(0L, page.getTotal());
        org.mockito.Mockito.verifyNoInteractions(deviceMapper, accessScopeService);
    }

    @Test
    void nameMatchesIntersectOriginalVisibilityAndReachContractCandidateQuery() {
        when(projects.findProjectIdsByName(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of(10L, 20L));
        when(customers.findCustomerIdsByName(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of(30L));
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of(10L));
        when(deviceMapper.selectVisibleDevicePage(org.mockito.ArgumentMatchers.any())).thenReturn(PageResult.empty());
        service.getPage(VisibleDevicePageQuery.builder().projectName("项目").customerName("客户")
                .departmentName(" 杭州 ").projectId(10L).pageNo(1).pageSize(10).build());
        org.mockito.Mockito.verify(deviceMapper).selectVisibleDevicePage(org.mockito.ArgumentMatchers.argThat(q ->
                q.visibleProjectIds().equals(Set.of(10L)) && q.matchingProjectIds().equals(Set.of(10L, 20L))
                        && q.matchingCustomerIds().equals(Set.of(30L)) && q.projectId().equals(10L)
                        && "杭州".equals(q.departmentName())));
        org.mockito.Mockito.verify(accessScopeService).contractScope(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.argThat(q -> q.matchingProjectIds().equals(Set.of(10L, 20L))
                        && q.matchingCustomerIds().equals(Set.of(30L)) && "杭州".equals(q.departmentName())));
    }

    @Test
    void shouldUseAuthenticatedTenantForDetailWhenTenantContextIsDisabled() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        when(deviceMapper.selectById(8L)).thenReturn(device);

        assertEquals(8L, service.getDevice(8L).getId());
    }
}
