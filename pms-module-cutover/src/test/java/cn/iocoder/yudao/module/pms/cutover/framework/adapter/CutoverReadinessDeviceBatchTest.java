package cn.iocoder.yudao.module.pms.cutover.framework.adapter;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessApi;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverReadinessDeviceBatchTest {
    final DeviceQueryApi devices = mock(DeviceQueryApi.class);
    final ImplementationReadinessApi readiness = mock(ImplementationReadinessApi.class);
    final ObjectProvider<ImplementationReadinessApi> provider = mock(ObjectProvider.class);
    final DeviceSummaryBatchQuery batch = new DeviceSummaryBatchQuery(1L, 7L, Set.of(41L, 42L), 9L);
    CutoverReadinessPort port;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
        when(provider.getIfAvailable()).thenReturn(readiness);
        port = new CutoverOwnerAdapters().cutoverReadinessPort(provider, devices);
    }

    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void inspectAndRevalidationEachPrefetchCurrentDevicesInsteadOfPerDeviceSummaries() {
        var first = device(41L, 9L); var second = device(42L, 9L);
        when(devices.getVisibleDeviceSummaries(batch)).thenReturn(List.of(first, second));
        when(devices.getDevice(41L)).thenReturn(first);
        when(devices.getDevice(42L)).thenReturn(second);
        var result = result();
        when(readiness.inspect(any())).thenReturn(result);
        when(readiness.lockAndRevalidate(any())).thenReturn(result);

        var expected = port.inspect(9L, List.of(41L, 42L));
        port.lockAndRevalidate(expected);

        verify(devices, times(2)).getVisibleDeviceSummaries(batch);
        verify(devices, never()).getDevice(anyLong());
        verify(readiness).inspect(new ImplementationReadinessQuery(1L, 9L, List.of(
                new ImplementationReadinessQuery.ExpectedDevice(41L, "SN-41", 3L),
                new ImplementationReadinessQuery.ExpectedDevice(42L, "SN-42", 3L))));
        verify(readiness).lockAndRevalidate(new ImplementationReadinessRevalidationQuery(1L, 9L, 71L, 0L, List.of(
                new ImplementationReadinessQuery.ExpectedDevice(41L, "SN-41", 3L),
                new ImplementationReadinessQuery.ExpectedDevice(42L, "SN-42", 3L))));
    }

    @Test void firstChangedProjectWinsBeforeALaterMissingDeviceAuthorizationFailure() {
        var changed = device(41L, 10L);
        when(devices.getVisibleDeviceSummaries(batch)).thenReturn(List.of(changed));

        var failure = assertThrows(CutoverOwnerFactException.class, () -> port.inspect(9L, List.of(41L, 42L)));

        assertEquals(CutoverOwnerFactException.Code.STALE, failure.code());
        verify(devices, never()).getDevice(anyLong());
        verifyNoInteractions(readiness);
    }

    @Test void missingSecondDeviceFallsBackAtItsOriginalPositionWithoutMaskingAuthorization() {
        var first = device(41L, 9L);
        when(devices.getVisibleDeviceSummaries(batch)).thenReturn(List.of(first));
        var denied = new AccessDeniedException("denied");
        when(devices.getDevice(42L)).thenThrow(denied);

        assertSame(denied, assertThrows(AccessDeniedException.class, () -> port.inspect(9L, List.of(41L, 42L))));
        verify(devices, never()).getDevice(41L);
        verifyNoInteractions(readiness);
    }

    private DeviceSummaryDTO device(Long id, Long project) {
        var value = mock(DeviceSummaryDTO.class);
        when(value.deviceId()).thenReturn(id); when(value.tenantId()).thenReturn(1L);
        when(value.projectId()).thenReturn(project); when(value.sn()).thenReturn("SN-" + id);
        when(value.projectAssignmentVersion()).thenReturn(3L);
        return value;
    }

    private ImplementationReadinessResult result() {
        var snapshot = mock(ImplementationReadinessSnapshotFact.class);
        var context = mock(ImplementationReadinessContextFact.class);
        when(context.devices()).thenReturn(List.of(
                new ImplementationReadinessContextFact.DeviceFact(41L, "SN-41", 3L),
                new ImplementationReadinessContextFact.DeviceFact(42L, "SN-42", 3L)));
        when(snapshot.tenantId()).thenReturn(1L); when(snapshot.projectId()).thenReturn(9L);
        when(snapshot.snapshotId()).thenReturn(71L); when(snapshot.snapshotVersion()).thenReturn(0L);
        when(snapshot.context()).thenReturn(context);
        var result = mock(ImplementationReadinessResult.class);
        when(result.snapshot()).thenReturn(snapshot); when(result.decision()).thenReturn(ImplementationReadinessResult.Decision.READY);
        when(result.currentUnmetCodes()).thenReturn(List.of());
        return result;
    }
}
