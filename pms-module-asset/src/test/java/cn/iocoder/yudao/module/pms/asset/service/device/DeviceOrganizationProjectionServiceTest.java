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
    @Test void contractOnlyRefreshUsesTheDedicatedCursorWhileMixedScopeKeepsItsUnion() {
        var mapper = mock(DeviceOrganizationMapper.class);
        var source = mock(DeviceOrganizationService.class);
        var contracts = mock(DeviceOrganizationContractRefreshService.class);
        var service = new DeviceOrganizationProjectionService(mapper, source, contracts);
        var contractOnly = new cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi.Refresh(1L, Set.of(), Set.of(), Set.of("A"));
        service.refresh(contractOnly);
        verify(contracts).refresh(contractOnly); verifyNoInteractions(mapper, source);
        when(mapper.selectBatchForUpdate(any())).thenReturn(List.of());
        service.refresh(new cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi.Refresh(1L, Set.of(8L), Set.of(), Set.of("A")));
        verify(mapper).selectBatchForUpdate(any()); verifyNoMoreInteractions(contracts);
    }
    @Test void unresolvedSourceClearsPreviousGrantAndRepeatedRebuildDoesNotWrite() {
        var mapper=mock(DeviceOrganizationMapper.class);
        var source=mock(DeviceOrganizationService.class);
        var service=new DeviceOrganizationProjectionService(mapper,source,mock(DeviceOrganizationContractRefreshService.class));
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
        assertThrows(IllegalStateException.class,()->new DeviceOrganizationProjectionService(mapper,source,mock(DeviceOrganizationContractRefreshService.class)).rebuildPage(1L,0));
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

    @Test void diverseOrganizationsUseFiveBoundedWritesForFiveHundredDevices() {
        var mapper=mock(DeviceOrganizationMapper.class);var source=mock(DeviceOrganizationService.class);
        var devices=new ArrayList<DeviceDO>();var facts=new LinkedHashMap<Long,DeviceOrganizationRespVO>();
        for(long id=1;id<=500;id++) {
            var row=new DeviceDO();row.setId(id);devices.add(row);
            long department=id%100;
            facts.put(id,new DeviceOrganizationRespVO(department%10,"Company "+department%10,
                    department,"D"+department,"Office "+department,"CONTRACT"));
        }
        when(mapper.selectBatchForUpdate(any())).thenReturn(devices);
        when(source.resolveSource(eq(7L),any())).thenReturn(facts);
        var captured=new ArrayList<DeviceOrganizationUpdate>();
        when(mapper.updateOrganizationRows(eq(7L),any())).thenAnswer(invocation->{
            List<DeviceOrganizationUpdate> rows=invocation.getArgument(1);
            assertTrue(rows.size()<=100);captured.addAll(rows);return rows.size();
        });
        var result=new DeviceOrganizationProjectionService(mapper,source,mock(DeviceOrganizationContractRefreshService.class)).rebuildPage(7L,0);
        assertEquals(500,result.updated());assertEquals(500,result.processed());assertEquals(500,result.afterId());
        verify(mapper,times(5)).updateOrganizationRows(eq(7L),any());
        verify(mapper,never()).updateOrganizationBatch(any(),any());
        assertEquals(500,captured.stream().map(DeviceOrganizationUpdate::deviceId).distinct().count());
        for(var update:captured) {
            assertEquals(7L,update.tenantId());var fact=facts.get(update.deviceId());
            assertEquals(fact.companyId(),update.companyId());assertEquals(fact.departmentId(),update.departmentId());
            assertEquals(fact.departmentCode(),update.departmentCode());assertEquals(fact.departmentName(),update.departmentName());
        }
    }

    @Test void mixedNullGrantsAndDelimiterContainingNamesRemainDistinct() {
        var mapper=mock(DeviceOrganizationMapper.class);var source=mock(DeviceOrganizationService.class);
        var devices=new ArrayList<DeviceDO>();
        for(long id=1;id<=3;id++){var row=new DeviceDO();row.setId(id);row.setCompanyId(99L);devices.add(row);}
        var facts=Map.of(1L,new DeviceOrganizationRespVO(1L,"A",2L,"x|y","z","CONTRACT"),
                2L,new DeviceOrganizationRespVO(1L,"A",2L,"x","y|z","CONTRACT"),
                3L,new DeviceOrganizationRespVO(null,null,null,null,null,"UNRESOLVED"));
        when(mapper.selectBatchForUpdate(any())).thenReturn(devices);
        when(source.resolveSource(eq(1L),any())).thenReturn(facts);
        when(mapper.updateOrganizationRows(eq(1L),any())).thenAnswer(invocation->{
            List<DeviceOrganizationUpdate> rows=invocation.getArgument(1);
            assertEquals(List.of(new DeviceOrganizationUpdate(1L,1L,1L,"A",2L,"x|y","z","CONTRACT"),
                    new DeviceOrganizationUpdate(1L,2L,1L,"A",2L,"x","y|z","CONTRACT"),
                    new DeviceOrganizationUpdate(1L,3L,null,null,null,null,null,"UNRESOLVED")),rows);
            return rows.size();
        });
        assertEquals(3,new DeviceOrganizationProjectionService(mapper,source,mock(DeviceOrganizationContractRefreshService.class)).rebuildPage(1L,0).updated());
        verify(mapper).updateOrganizationRows(eq(1L),any());verify(mapper,never()).updateOrganizationBatch(any(),any());
    }

    @Test void emptyOrAlreadyCurrentPageDoesNotInvokeEitherWriter() {
        var mapper=mock(DeviceOrganizationMapper.class);var source=mock(DeviceOrganizationService.class);
        when(mapper.selectBatchForUpdate(any())).thenReturn(List.of());
        when(source.resolveSource(eq(1L),any())).thenReturn(Map.of());
        assertEquals(0,new DeviceOrganizationProjectionService(mapper,source,mock(DeviceOrganizationContractRefreshService.class)).rebuildPage(1L,0).updated());
        verify(mapper,never()).updateOrganizationRows(any(),any());verify(mapper,never()).updateOrganizationBatch(any(),any());
    }
}
