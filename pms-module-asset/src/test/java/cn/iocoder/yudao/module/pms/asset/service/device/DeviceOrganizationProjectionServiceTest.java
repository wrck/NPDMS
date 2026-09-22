package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceOrganizationRespVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationUpdate;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceOrganizationProjectionServiceTest {
    @Test void unresolvedSourceClearsPreviousGrantAndRepeatedRebuildDoesNotWrite() {
        var mapper=mock(DeviceOrganizationMapper.class);
        var source=mock(DeviceOrganizationService.class);
        var service=new DeviceOrganizationProjectionService(mapper,source);
        var device=new DeviceDO();device.setId(8L);device.setCompanyId(1L);device.setDepartmentId(2L);
        device.setOrganizationSource("CONTRACT");device.setOrganizationUpdatedAt(LocalDateTime.now());
        when(mapper.selectBatchForUpdate(any())).thenReturn(List.of(device));
        when(source.resolveSource(eq(1L),any())).thenReturn(Map.of(8L,new DeviceOrganizationRespVO(null,null,null,null,null,"UNRESOLVED")));
        when(mapper.updateOrganization(any())).thenReturn(1);
        assertEquals(1,service.rebuildPage(1L,0).updated());
        verify(mapper).updateOrganization(new DeviceOrganizationUpdate(1L,8L,null,null,null,null,null,"UNRESOLVED"));
        device.setCompanyId(null);device.setDepartmentId(null);device.setOrganizationSource("UNRESOLVED");
        clearInvocations(mapper);
        assertEquals(0,service.rebuildPage(1L,0).updated());
        verify(mapper,never()).updateOrganization(any());
    }

    @Test void ownerUnavailableAbortsRebuildRatherThanPersistingUnknownFacts() {
        var mapper=mock(DeviceOrganizationMapper.class);var source=mock(DeviceOrganizationService.class);
        var device=new DeviceDO();device.setId(8L);
        when(mapper.selectBatchForUpdate(any())).thenReturn(List.of(device));
        when(source.resolveSource(eq(1L),any())).thenThrow(new IllegalStateException("Owner unavailable"));
        assertThrows(IllegalStateException.class,()->new DeviceOrganizationProjectionService(mapper,source).rebuildPage(1L,0));
        verify(mapper,never()).updateOrganization(any());
    }

    @Test void projectOwnershipOverridesContractAndClearsMissingProjectOwnership() {
        var projects=mock(cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi.class);
        var contracts=mock(cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi.class);
        var service=new DeviceOrganizationService(projects,contracts,mock(DeviceOrganizationMapper.class));
        when(projects.getOrganizations(1L,Set.of(10L))).thenReturn(List.of(
                new cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi.Organization(10L,2L,"Company2",20L,"D20","Office20")));
        var devices=List.of(new DeviceOrganizationService.Device(8L,10L,"C-1"));
        assertEquals(2L,service.resolveSource(1L,devices).get(8L).companyId());
        verifyNoInteractions(contracts);
        when(projects.getOrganizations(1L,Set.of(10L))).thenReturn(List.of());
        assertNull(service.resolveSource(1L,devices).get(8L).companyId());
    }
}
