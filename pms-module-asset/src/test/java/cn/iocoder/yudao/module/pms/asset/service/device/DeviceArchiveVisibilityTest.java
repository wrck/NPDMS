package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchivePageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceVersionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceArchiveVisibilityTest {
    private final DeviceMapper devices = mock(DeviceMapper.class);
    private final DeviceVersionMapper versions = mock(DeviceVersionMapper.class);
    private final DeviceAccessScopeService scope = mock(DeviceAccessScopeService.class);
    private final ProjectDeviceSelectionService selection = mock(ProjectDeviceSelectionService.class);
    private final DeviceArchiveServiceImpl service = new DeviceArchiveServiceImpl(
            devices, versions, mock(CustomerQueryApi.class), selection, scope, org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi.class));
    @BeforeEach void login() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(7L); user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void listPassesOnlyServerResolvedScopeAndKeepsFilters() {
        var request = new DeviceArchivePageReqVO(); request.setName("router"); request.setStatus("IN_USE");
        request.setProjectId(10L); request.setPageNo(1); request.setPageSize(10);
        when(scope.visibleProjectIds(1L, 7L)).thenReturn(Set.of(10L));
        var query = VisibleDevicePageQuery.builder()
                .tenantId(1L)
                .visibleProjectIds(Set.of(10L))
                .projectId(10L)
                .pageNo(1)
                .pageSize(10)
                .name("router")
                .status("IN_USE")
                .visibleContractNumbers(Set.of())
                .organizationGrants(java.util.List.of()).build();
        when(devices.selectArchivePage(query)).thenReturn(PageResult.empty());
        assertEquals(0L, service.getDeviceArchivePage(request).getTotal());
        verify(devices).selectArchivePage(query);
    }
    @Test void invisibleRecordAndHistoryDoNotReadData() {
        doThrow(new IllegalStateException("inaccessible")).when(scope).assertVisible(1L, 7L, 99L);
        assertThrows(IllegalStateException.class, () -> service.getDeviceArchiveRecord(99L));
        assertThrows(IllegalStateException.class, () -> service.getDeviceVersionList(99L));
        assertThrows(IllegalStateException.class, () -> service.deleteDevice(99L));
        verifyNoInteractions(devices, versions);
    }
    @Test void emptyScopeReturnsNoRowsWithoutRunningSql() {
        var mapper = mock(DeviceMapper.class, CALLS_REAL_METHODS);
        var query = VisibleDevicePageQuery.builder()
                .tenantId(1L)
                .visibleProjectIds(Set.of())
                .pageNo(1)
                .pageSize(10)
                .visibleContractNumbers(Set.of())
                .organizationGrants(java.util.List.of()).build();
        assertEquals(0L, mapper.selectArchivePage(query).getTotal());
        verify(mapper, never()).selectVisibleArchiveList(query);
        verify(mapper, never()).selectVisibleDeviceCount(query);
    }
    @Test void businessPickerRetainsItsOwnProjectAuthorization() {
        var request = new DeviceArchivePageReqVO(); request.setSelectionProjectId(10L);
        when(selection.getPage(request)).thenReturn(PageResult.empty());
        assertEquals(0L, service.getDeviceArchivePage(request).getTotal());
        verifyNoInteractions(scope, devices);
    }
}
