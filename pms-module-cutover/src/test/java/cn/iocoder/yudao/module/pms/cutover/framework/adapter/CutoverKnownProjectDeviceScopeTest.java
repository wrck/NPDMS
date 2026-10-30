package cn.iocoder.yudao.module.pms.cutover.framework.adapter;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.*;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverKnownProjectDeviceScopeTest {
    final DeviceQueryApi devices = mock(DeviceQueryApi.class);
    final DeviceScopeFactApi facts = mock(DeviceScopeFactApi.class);
    final ProjectScopeApi projects = mock(ProjectScopeApi.class);
    final CutoverDeviceScopePort port = new CutoverOwnerAdapters().cutoverDeviceScopePort(devices, facts, projects);
    final DeviceScopeResolveQuery query = new DeviceScopeResolveQuery(1L, 9L, List.of("SN-42", "SN-41"));

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void knownProjectResolvesAllSerialsInOneOwnerCallWithoutProjectDiscovery() {
        var fact = new DeviceScopeFact(1L, 9L, List.of(
                new DeviceScopeFact.Device(42L, "SN-42", 9L, 4L),
                new DeviceScopeFact.Device(41L, "SN-41", 9L, 3L)),
                new DeviceScopeFact.ScopeWatermark(List.of(
                        new DeviceScopeFact.WatermarkEntry(42L, 4L), new DeviceScopeFact.WatermarkEntry(41L, 3L))));
        when(facts.resolveBySerials(query)).thenReturn(new DeviceScopeResolutionResult(
                DeviceScopeResolutionResult.Decision.RESOLVED, fact, List.of()));

        assertEquals(List.of(new CutoverDeviceScopePort.DeviceFact(41L, "SN-41", 9L, 3L),
                new CutoverDeviceScopePort.DeviceFact(42L, "SN-42", 9L, 4L)), port.resolveBySerials(query));

        verify(facts).resolveBySerials(query);
        verifyNoInteractions(devices, projects);
    }

    @Test void missingIneligibleOrForeignProjectSerialKeepsTheWholeScopeInvalid() {
        for (var reason : DeviceScopeInvalidItem.Reason.values()) {
            clearInvocations(facts);
            when(facts.resolveBySerials(query)).thenReturn(new DeviceScopeResolutionResult(
                    DeviceScopeResolutionResult.Decision.INVALID, null, List.of(
                    new DeviceScopeInvalidItem(reason == DeviceScopeInvalidItem.Reason.NOT_FOUND ? null : 42L, "SN-42", reason))));

            assertTrue(port.resolveBySerials(query).isEmpty(), reason.name());

            verify(facts).resolveBySerials(query);
        }
        verifyNoInteractions(devices, projects);
    }

    @Test void ownerFailureIsNotReinterpretedAsMissingOrRetriedThroughGlobalDiscovery() {
        var unavailable = new DeviceScopeFactException(DeviceScopeFactException.Code.PROVIDER_UNAVAILABLE, "offline");
        when(facts.resolveBySerials(query)).thenThrow(unavailable);

        assertSame(unavailable, assertThrows(DeviceScopeFactException.class, () -> port.resolveBySerials(query)));

        verifyNoInteractions(devices, projects);
    }
}
