package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.*;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommands.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException.Code.OWNER_DATA_CORRUPTED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceAndLocationFactAdapterTest {

    private static final Long TENANT = 1L;
    private static final Long PROJECT = 10L;

    private final DeviceScopeFactApi api = mock(DeviceScopeFactApi.class);
    private final DeviceAndLocationFactAdapter adapter = new DeviceAndLocationFactAdapter(
            api, mock(AssetLocationApi.class));

    @Test
    void inspectAcceptsNormalizedSerialIdentityAndKeepsOwnerValue() {
        when(api.resolveBySerials(any())).thenReturn(new DeviceScopeResolutionResult(
                DeviceScopeResolutionResult.Decision.RESOLVED, fact(TENANT, "SN-1"), List.of()));

        DeviceAndLocationFactAdapter.Snapshot result = adapter.inspect(TENANT, PROJECT, lines(" sn-1 "));

        assertEquals("SN-1", result.devices().getFirst().serialNumber());
        assertEquals(11L, result.devices().getFirst().deviceId());
    }

    @Test
    void inspectRejectsOwnerFactFromDifferentTenant() {
        when(api.resolveBySerials(any())).thenReturn(new DeviceScopeResolutionResult(
                DeviceScopeResolutionResult.Decision.RESOLVED, fact(2L, "SN-1"), List.of()));

        CommerceDeliveryScopeCommandException failure = assertThrows(
                CommerceDeliveryScopeCommandException.class,
                () -> adapter.inspect(TENANT, PROJECT, lines("SN-1")));

        assertEquals(OWNER_DATA_CORRUPTED, failure.getCode());
    }

    @Test
    void lockAcceptsEquivalentSerialWithoutRewritingFrozenOwnerValue() {
        DeviceAndLocationFactAdapter.Snapshot expected = new DeviceAndLocationFactAdapter.Snapshot(List.of(
                new DeviceAndLocationFactAdapter.Device(11L, "Sn-1", PROJECT, 3L)));
        when(api.lockAndRevalidate(any())).thenReturn(new DeviceScopeRevalidationResult(
                DeviceScopeRevalidationResult.Decision.VALID, fact(TENANT, " sn-1 "), List.of()));

        assertDoesNotThrow(() -> adapter.lockAndRevalidate(TENANT, PROJECT, expected, lines("SN-1")));
        verify(api).lockAndRevalidate(argThat(query -> "Sn-1".equals(
                query.expectedDevices().getFirst().serialNumber())));
    }

    private DeviceScopeFact fact(Long tenantId, String serial) {
        DeviceScopeFact.Device device = new DeviceScopeFact.Device(11L, serial, PROJECT, 3L);
        return new DeviceScopeFact(tenantId, PROJECT, List.of(device),
                new DeviceScopeFact.ScopeWatermark(List.of(
                        new DeviceScopeFact.WatermarkEntry(11L, 3L))));
    }

    private List<ScopeLine> lines(String serial) {
        Location location = new Location(LocationResolution.UNRESOLVED, null, null, null, null, "机房A");
        ScopeDetail detail = new ScopeDetail("OFFICE-A", BigDecimal.ONE, "EA", "P-1", "M-1",
                serial, location);
        return List.of(new ScopeLine(30L, "V1", BigDecimal.ONE, "EA", List.of(detail)));
    }
}
