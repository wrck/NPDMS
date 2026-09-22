package cn.iocoder.yudao.module.pms.asset.service.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceVisibilityQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAccessScopeServiceTest {

    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private DeviceMapper deviceMapper;
    @Mock private cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi organizations;
    @Mock private cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi contracts;

    @Test
    void organizationGrantAloneAllowsDeviceLookup() {
        var service = new DeviceAccessScopeService(projectScopeApi, deviceMapper, contracts, organizations);
        var grant = new cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO();
        grant.setId(1L); grant.setVersion(1); grant.setCompanyId(20L);
        when(organizations.getActiveScopes(7L)).thenReturn(java.util.List.of(grant));
        var query=new DeviceVisibilityQuery(1L,8L,Set.of(),Set.of(),java.util.List.of(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant(20L,null,null)));
        when(deviceMapper.existsVisibleDevice(query)).thenReturn(true);
        service.assertVisible(1L,7L,8L);
        verify(deviceMapper).existsVisibleDevice(query);
        when(organizations.getActiveScopes(7L)).thenReturn(java.util.List.of());
        assertThrows(ServiceException.class, () -> service.assertVisible(1L,7L,8L));
    }

    @Test
    void contractOnlyScopeIsPassedToSharedVisibilityQuery() {
        var service = new DeviceAccessScopeService(projectScopeApi, deviceMapper, contracts, organizations);
        when(deviceMapper.selectContractCandidates(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of("C-1"));
        when(contracts.getVisibleContractNumbers(1L, 7L, Set.of("C-1"))).thenReturn(Set.of("C-1"));
        var query = new DeviceVisibilityQuery(1L, 8L, Set.of(), Set.of("C-1"), java.util.List.of());
        when(deviceMapper.existsVisibleDevice(query)).thenReturn(true);
        service.assertVisible(1L, 7L, 8L);
        verify(deviceMapper).existsVisibleDevice(query);
    }

    @Test
    void contractOwnerFailureCannotGrantVisibility() {
        var service = new DeviceAccessScopeService(projectScopeApi, deviceMapper, contracts, organizations);
        when(deviceMapper.selectContractCandidates(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of("C-1"));
        when(contracts.getVisibleContractNumbers(1L, 7L, Set.of("C-1"))).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(ServiceException.class, () -> service.assertVisible(1L, 7L, 8L));
        verify(deviceMapper, never()).existsVisibleDevice(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldUseProjectOwnerScopeForDeviceVisibility() {
        DeviceAccessScopeService service = new DeviceAccessScopeService(projectScopeApi, deviceMapper, contracts, organizations);
        ProjectAllScopeQuery scopeQuery = new ProjectAllScopeQuery(1L, 7L, ProjectScopeApi.ACTION_VIEW);
        when(projectScopeApi.resolveAllCurrent(scopeQuery)).thenReturn(Set.of(10L, 11L));
        DeviceVisibilityQuery visibilityQuery = new DeviceVisibilityQuery(1L, 8L, Set.of(10L, 11L), Set.of(), java.util.List.of());
        when(deviceMapper.existsVisibleDevice(visibilityQuery)).thenReturn(true);

        service.assertVisible(1L, 7L, 8L);

        verify(deviceMapper).existsVisibleDevice(visibilityQuery);
    }

    @Test
    void shouldRejectBeforeDeviceQueryWhenProjectScopeIsEmpty() {
        DeviceAccessScopeService service = new DeviceAccessScopeService(projectScopeApi, deviceMapper, contracts, organizations);
        when(projectScopeApi.resolveAllCurrent(
                new ProjectAllScopeQuery(1L, 7L, ProjectScopeApi.ACTION_VIEW))).thenReturn(Set.of());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.assertVisible(1L, 7L, 8L));

        assertEquals(AST_EQUIPMENT_NOT_EXISTS.getCode(), error.getCode());
        verify(deviceMapper, never()).existsVisibleDevice(org.mockito.ArgumentMatchers.any());
    }
}
