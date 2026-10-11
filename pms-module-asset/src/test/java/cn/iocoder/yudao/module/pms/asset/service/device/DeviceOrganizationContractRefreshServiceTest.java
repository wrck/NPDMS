package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceContractOrganizationBatchQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationUpdate;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceOrganizationContractRefreshServiceTest {
    @Test void contractCursorKeepsLowerIdsInLaterContractsAndResolvesOwnerOnlyOnce() {
        var mapper = mock(DeviceOrganizationMapper.class);
        var owner = mock(ContractDeviceVisibilityApi.class);
        var command = new DeviceOrganizationProjectionApi.Refresh(1L, Set.of(), Set.of(), Set.of("A", "B"));
        var first = device(100L, "A");
        var unchanged = device(200L, "A");
        unchanged.setCompanyId(1L); unchanged.setCompanyName("Company");
        unchanged.setOrganizationSource("CONTRACT"); unchanged.setOrganizationUpdatedAt(LocalDateTime.now());
        var missing = device(10L, "B");
        missing.setCompanyId(9L); missing.setDepartmentId(90L);
        missing.setOrganizationSource("CONTRACT"); missing.setOrganizationUpdatedAt(LocalDateTime.now());
        when(mapper.selectContractBatchForUpdate(any())).thenReturn(List.of(first, unchanged), List.of(missing), List.of());
        when(owner.getOrganizations(1L, command.contractNumbers())).thenReturn(List.of(
                new ContractDeviceVisibilityApi.Organization("A", 1L, "Company", null, null, null)));
        when(mapper.updateOrganizationBatch(any(), any())).thenReturn(1);
        new DeviceOrganizationContractRefreshService(mapper, owner).refresh(command);
        verify(mapper).selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, command.contractNumbers(), null, 0, 500));
        verify(mapper).selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, command.contractNumbers(), "A", 200, 500));
        verify(mapper).selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(1L, command.contractNumbers(), "B", 10, 500));
        verify(owner, times(1)).getOrganizations(1L, command.contractNumbers());
        verify(mapper).updateOrganizationBatch(new DeviceOrganizationUpdate(1L, null, 1L, "Company", null, null, null, "CONTRACT"), List.of(100L));
        verify(mapper).updateOrganizationBatch(new DeviceOrganizationUpdate(1L, null, null, null, null, null, null, "UNRESOLVED"), List.of(10L));
        verify(mapper, times(2)).updateOrganizationBatch(any(), any());
    }

    @Test void ownerUnavailableAbortsBeforeAnyOrganizationWrite() {
        var mapper = mock(DeviceOrganizationMapper.class);
        var owner = mock(ContractDeviceVisibilityApi.class);
        when(mapper.selectContractBatchForUpdate(any())).thenReturn(List.of(device(1L, "A")));
        when(owner.getOrganizations(any(), any())).thenThrow(new IllegalStateException("Owner unavailable"));
        assertThrows(IllegalStateException.class, () -> new DeviceOrganizationContractRefreshService(mapper, owner)
                .refresh(new DeviceOrganizationProjectionApi.Refresh(1L, Set.of(), Set.of(), Set.of("A"))));
        verify(mapper, never()).updateOrganizationBatch(any(), any());
    }

    @Test void incompleteUpdateAbortsTheSyncChunk() {
        var mapper = mock(DeviceOrganizationMapper.class);
        var owner = mock(ContractDeviceVisibilityApi.class);
        when(mapper.selectContractBatchForUpdate(any())).thenReturn(List.of(device(1L, "A")));
        when(owner.getOrganizations(any(), any())).thenReturn(List.of());
        when(mapper.updateOrganizationBatch(any(), any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> new DeviceOrganizationContractRefreshService(mapper, owner)
                .refresh(new DeviceOrganizationProjectionApi.Refresh(1L, Set.of(), Set.of(), Set.of("A"))));
        verify(mapper, times(1)).selectContractBatchForUpdate(any());
    }

    private DeviceDO device(Long id, String contract) {
        var device = new DeviceDO(); device.setId(id); device.setContractNo(contract); return device;
    }
}
