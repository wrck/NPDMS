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
        var device9=new DeviceDO();device9.setId(9L);device9.setCompanyId(1L);device9.setDepartmentId(2L);
        device9.setOrganizationSource("CONTRACT");device9.setOrganizationUpdatedAt(LocalDateTime.now());
        when(mapper.selectBatchForUpdate(any())).thenReturn(List.of(device,device9));
        when(source.resolveSource(eq(1L),any())).thenReturn(Map.of(8L,new DeviceOrganizationRespVO(null,null,null,null,null,"UNRESOLVED"),
                9L,new DeviceOrganizationRespVO(null,null,null,null,null,"UNRESOLVED")));
        when(mapper.updateOrganizationBatch(any(),any())).thenReturn(2);
        assertEquals(2,service.rebuildPage(1L,0).updated());
        // 同一归属事实合并为一次 IN 批量更新
        verify(mapper).updateOrganizationBatch(new DeviceOrganizationUpdate(1L,null,null,null,null,null,null,"UNRESOLVED"),List.of(8L,9L));
        device.setCompanyId(null);device.setDepartmentId(null);device.setOrganizationSource("UNRESOLVED");
        device9.setCompanyId(null);device9.setDepartmentId(null);device9.setOrganizationSource("UNRESOLVED");
        clearInvocations(mapper);
        assertEquals(0,service.rebuildPage(1L,0).updated());
        verify(mapper,never()).updateOrganizationBatch(any(),any());
    }

    @Test void ownerUnavailableAbortsRebuildRatherThanPersistingUnknownFacts() {
        var mapper=mock(DeviceOrganizationMapper.class);var source=mock(DeviceOrganizationService.class);
        var device=new DeviceDO();device.setId(8L);
        when(mapper.selectBatchForUpdate(any())).thenReturn(List.of(device));
        when(source.resolveSource(eq(1L),any())).thenThrow(new IllegalStateException("Owner unavailable"));
        assertThrows(IllegalStateException.class,()->new DeviceOrganizationProjectionService(mapper,source).rebuildPage(1L,0));
        verify(mapper,never()).updateOrganizationBatch(any(),any());
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
