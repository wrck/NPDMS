package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceCurrentModelApiTest {
    private final DeviceMapper writes = mock(DeviceMapper.class);
    private final DeviceQueryService reads = mock(DeviceQueryService.class);
    private final DeviceQueryApiImpl api = new DeviceQueryApiImpl(writes, reads);

    private DeviceDO device(Long id, String model) {
        var device = new DeviceDO(); device.setId(id); device.setTenantId(1L);
        device.setSn("SN-" + id); device.setProductCode("C" + id); device.setProductModel(model);
        device.setVersion(7L); return device;
    }
    @Test void currentSummaryUsesTheAuthorizedSourceProjectionWithoutMutatingIt() {
        var source = device(1L, "CRM-MODEL");
        source.setProductDesc("CURRENT-DESCRIPTION");
        when(reads.getDevice(1L)).thenReturn(source);
        assertEquals("CRM-MODEL", api.getDevice(1L).productModel());
        assertEquals("CURRENT-DESCRIPTION", cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(api.getDevice(1L))).path("productDesc").asText());
        assertEquals("CRM-MODEL", source.getProductModel()); assertEquals(7L, source.getVersion());
        verify(reads, times(2)).getDevice(1L); verifyNoMoreInteractions(reads);
        verifyNoInteractions(writes);
    }
    @Test void existingModelWinsWithoutLookingUpCrm() {
        when(reads.getDevice(2L)).thenReturn(device(2L, "ORIGINAL"));
        assertEquals("ORIGINAL", api.getDevice(2L).productModel());
        verifyNoInteractions(writes);
    }
    @Test void deniedDeviceNeverReadsModelOrWritesData() {
        when(reads.getDevice(1L)).thenThrow(new AccessDeniedException("denied"));
        assertThrows(AccessDeniedException.class, () -> api.getDevice(1L));
        verifyNoInteractions(writes);
    }
    @Test void projectSummaryConsumesTheSourceProjectionAndKeepsMissingModelsUnknown() {
        var query = new ProjectDeviceInstallationQuery(1L, 10L);
        when(reads.getProjectDevices(query)).thenReturn(List.of(device(1L, "CRM-MODEL"), device(2L, "ORIGINAL"), device(3L, null)));
        var result = api.getProjectDeviceSummaries(query);
        assertEquals("CRM-MODEL", result.get(0).productModel());
        assertEquals("ORIGINAL", result.get(1).productModel()); assertNull(result.get(2).productModel());
        verify(reads).getProjectDevices(query); verifyNoMoreInteractions(reads); verifyNoInteractions(writes);
    }

    @Test void visibleBatchUsesOnlyReturnedSourceRowsAndCarriesOptionalCurrentFields() {
        var query = new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(1L, 7L, Set.of(1L, 2L, 3L), null);
        var first = device(1L, "MASTER"); first.setName("设备甲"); first.setLocationSnapshot("机房A");
        when(reads.getVisibleDevices(query)).thenReturn(List.of(first, device(2L, "LOCAL")));

        var result = api.getVisibleDeviceSummaries(query);

        assertEquals(List.of(1L, 2L), result.stream().map(value -> value.deviceId()).toList());
        assertEquals("MASTER", result.getFirst().productModel());
        assertEquals("设备甲", result.getFirst().name()); assertEquals("机房A", result.getFirst().locationSnapshot());
        assertEquals("LOCAL", result.get(1).productModel());
        assertNull(result.get(1).name()); assertNull(result.get(1).locationSnapshot());
        String json = cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(result);
        assertTrue(json.contains("设备甲")); assertTrue(json.contains("机房A"));
        verify(reads).getVisibleDevices(query); verifyNoMoreInteractions(reads); verifyNoInteractions(writes);
    }
}
