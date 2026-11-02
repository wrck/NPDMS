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
        doThrow(new IllegalStateException("inaccessible")).when(scope).assertReadable(new DeviceAccessScopeService.ReadAccess(1L, 7L, 99L));
        assertThrows(IllegalStateException.class, () -> service.getDeviceArchiveRecord(99L));
        assertThrows(IllegalStateException.class, () -> service.getDeviceVersionList(99L));
        assertThrows(IllegalStateException.class, () -> service.deleteDevice(99L));
        verifyNoInteractions(devices, versions);
    }
    @Test void readableArchiveDoesNotGrantMutationScope() {
        var row = new cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO();
        row.setId(9L); row.setTenantId(1L);
        when(devices.selectByTenantAndId(1L, 9L)).thenReturn(row);
        doThrow(new IllegalStateException("write denied")).when(scope).assertVisible(1L, 7L, 9L);
        assertSame(row, service.getDeviceArchiveRecord(9L));
        service.getDeviceVersionList(9L);
        verify(versions).selectListByDeviceId(9L);
        assertThrows(IllegalStateException.class, () -> service.deleteDevice(9L));
        verify(devices, never()).deleteById(anyLong());
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
    @Test void archiveReadKeepsStoredValuesForEditorsAndExposesTheirDescription() {
        var raw = new cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO();
        raw.setId(9L); raw.setTenantId(1L); raw.setSn("RAW-SN");
        raw.setProductModel(null); raw.setProductName(null); raw.setProductDesc("STORED-DESCRIPTION");
        when(devices.selectByTenantAndId(1L, 9L)).thenReturn(raw);

        var result = service.getDeviceArchiveRecord(9L);
        var response = cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(result,
                cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveRespVO.class);
        var json = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(response));

        assertSame(raw, result);
        assertNull(response.getProductModel()); assertNull(response.getProductName());
        assertEquals("STORED-DESCRIPTION", json.path("productDesc").asText());
        verify(scope).assertReadable(new DeviceAccessScopeService.ReadAccess(1L, 7L, 9L));
        verifyNoInteractions(versions);
        verify(devices, never()).updateById(any(cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO.class));
    }
}
