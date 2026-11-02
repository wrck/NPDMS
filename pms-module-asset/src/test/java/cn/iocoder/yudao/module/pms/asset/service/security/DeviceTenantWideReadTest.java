package cn.iocoder.yudao.module.pms.asset.service.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceTenantWideReadTest {
    final DeviceMapper devices = mock(DeviceMapper.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final DeviceAccessScopeService service = new DeviceAccessScopeService(mock(ProjectScopeApi.class), devices,
            mock(ContractDeviceVisibilityApi.class), mock(OrganizationScopeApi.class), mock(ProjectViewReadScopeApi.class), permissions);
    final ProjectAllScopeQuery subject = new ProjectAllScopeQuery(1L, 7L, ProjectScopeApi.ACTION_VIEW);
    final DeviceAccessScopeService.ReadAccess device = new DeviceAccessScopeService.ReadAccess(1L, 7L, 8L);

    @BeforeEach void login() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void superAdministratorCanReadUnassignedDeviceButDoesNotGainWriteScope() {
        when(permissions.hasAnyRoles(7L, "super_admin")).thenReturn(true);
        var row = new DeviceDO(); row.setId(8L); row.setTenantId(1L);
        when(devices.selectByTenantAndId(1L, 8L)).thenReturn(row);
        assertTrue(service.hasTenantWideRead(subject));
        assertDoesNotThrow(() -> service.assertReadable(device));
        assertThrows(ServiceException.class, () -> service.assertVisible(1L, 7L, 8L));
        assertThrows(ServiceException.class, () -> service.assertReadable(new DeviceAccessScopeService.ReadAccess(1L, 7L, 9L)));
    }

    @Test void ordinaryAccountAndFailedRoleLookupCannotGrantTenantWideRead() {
        assertFalse(service.hasTenantWideRead(subject));
        assertThrows(ServiceException.class, () -> service.assertReadable(device));
        verify(devices, never()).selectByTenantAndId(anyLong(), anyLong());
        when(permissions.hasAnyRoles(7L, "super_admin")).thenThrow(new IllegalStateException("role owner unavailable"));
        assertThrows(IllegalStateException.class, () -> service.assertReadable(device));
        verify(devices, never()).selectByTenantAndId(anyLong(), anyLong());
    }

    @Test void forgedTenantActorWriteActionAndMissingSessionAreRejectedBeforeRoleOrDeviceRead() {
        assertFalse(service.hasTenantWideRead(new ProjectAllScopeQuery(2L, 7L, ProjectScopeApi.ACTION_VIEW)));
        assertFalse(service.hasTenantWideRead(new ProjectAllScopeQuery(1L, 9L, ProjectScopeApi.ACTION_VIEW)));
        assertFalse(service.hasTenantWideRead(new ProjectAllScopeQuery(1L, 7L, ProjectScopeApi.ACTION_EDIT)));
        assertThrows(ServiceException.class, () -> service.assertReadable(new DeviceAccessScopeService.ReadAccess(2L, 7L, 8L)));
        assertThrows(ServiceException.class, () -> service.assertReadable(new DeviceAccessScopeService.ReadAccess(1L, 9L, 8L)));
        TenantContextHolder.setTenantId(2L);
        assertFalse(service.hasTenantWideRead(new ProjectAllScopeQuery(2L, 7L, ProjectScopeApi.ACTION_VIEW)));
        SecurityContextHolder.clearContext();
        assertFalse(service.hasTenantWideRead(subject));
        assertThrows(ServiceException.class, () -> service.assertReadable(device));
        verifyNoInteractions(permissions, devices);
    }
}
