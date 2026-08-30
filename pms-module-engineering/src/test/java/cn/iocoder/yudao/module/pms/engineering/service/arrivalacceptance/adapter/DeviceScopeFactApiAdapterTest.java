package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.adapter;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeFact;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeInvalidItem;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolutionResult;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationResult;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceContractException;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.OwnerFactVersionMismatchException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceScopeFactApiAdapterTest {

    private static final Long TENANT_ID = 1L;
    private static final Long PROJECT_ID = 100L;

    @Test
    void mapsResolvedFactAndPassesTrustedResolveInputsWithoutSubstitution() {
        DeviceScopeFactApi api = mock(DeviceScopeFactApi.class);
        when(api.resolveBySerials(any())).thenReturn(new DeviceScopeResolutionResult(
                DeviceScopeResolutionResult.Decision.RESOLVED, fact(), List.of()));
        DeviceScopeFactPort adapter = new DeviceScopeFactApiAdapter(api);

        DeviceScopeFactPort.DeviceScopeFact result = adapter.resolveBySerials(
                TENANT_ID, PROJECT_ID, Set.of("SN-2", "SN-1"));

        assertEquals(PROJECT_ID, result.projectId());
        assertEquals(List.of(10L, 20L), result.devices().stream()
                .map(DeviceScopeFactPort.DeviceFact::deviceId).toList());
        assertEquals(List.of(3L, 4L), result.devices().stream()
                .map(DeviceScopeFactPort.DeviceFact::projectAssignmentVersion).toList());
        ArgumentCaptor<DeviceScopeResolveQuery> query = ArgumentCaptor.forClass(DeviceScopeResolveQuery.class);
        verify(api).resolveBySerials(query.capture());
        assertEquals(TENANT_ID, query.getValue().tenantId());
        assertEquals(PROJECT_ID, query.getValue().projectId());
        assertEquals(Set.of("SN-1", "SN-2"), Set.copyOf(query.getValue().serialNumbers()));
    }

    @Test
    void mapsValidFactAndBuildsExactExpectedWatermarkFromFrozenDevices() {
        DeviceScopeFactApi api = mock(DeviceScopeFactApi.class);
        when(api.lockAndRevalidate(any())).thenReturn(new DeviceScopeRevalidationResult(
                DeviceScopeRevalidationResult.Decision.VALID, fact(), List.of()));
        DeviceScopeFactPort adapter = new DeviceScopeFactApiAdapter(api);

        DeviceScopeFactPort.DeviceScopeFact result = adapter.lockAndRevalidate(
                TENANT_ID, PROJECT_ID, List.of(
                        new DeviceScopeFactPort.ExpectedDeviceFact(20L, "SN-2", 4L),
                        new DeviceScopeFactPort.ExpectedDeviceFact(10L, "SN-1", 3L)));

        assertEquals(List.of(10L, 20L), result.devices().stream()
                .map(DeviceScopeFactPort.DeviceFact::deviceId).toList());
        ArgumentCaptor<DeviceScopeRevalidationQuery> query =
                ArgumentCaptor.forClass(DeviceScopeRevalidationQuery.class);
        verify(api).lockAndRevalidate(query.capture());
        assertEquals(TENANT_ID, query.getValue().tenantId());
        assertEquals(PROJECT_ID, query.getValue().projectId());
        assertEquals(List.of(10L, 20L), query.getValue().expectedDevices().stream()
                .map(DeviceScopeRevalidationQuery.ExpectedDevice::deviceId).toList());
        assertEquals(List.of(
                        new DeviceScopeRevalidationQuery.ExpectedWatermarkEntry(10L, 3L),
                        new DeviceScopeRevalidationQuery.ExpectedWatermarkEntry(20L, 4L)),
                query.getValue().expectedScopeWatermark().entries());
    }

    @Test
    void initialInvalidBecomesBusinessGateWithoutLeakingInvalidItems() {
        DeviceScopeFactApi api = mock(DeviceScopeFactApi.class);
        when(api.resolveBySerials(any())).thenReturn(new DeviceScopeResolutionResult(
                DeviceScopeResolutionResult.Decision.INVALID, null,
                List.of(new DeviceScopeInvalidItem(null, "SN-404",
                        DeviceScopeInvalidItem.Reason.NOT_FOUND))));
        DeviceScopeFactPort adapter = new DeviceScopeFactApiAdapter(api);

        ArrivalAcceptanceContractException exception = assertThrows(
                ArrivalAcceptanceContractException.class,
                () -> adapter.resolveBySerials(TENANT_ID, PROJECT_ID, Set.of("SN-404")));

        assertEquals("BUSINESS_GATE_INVALID", exception.category());
        assertEquals("DEVICE_SCOPE_INVALID", exception.reasonCode());
        assertEquals(null, exception.ownerContext());
        assertEquals(false, exception.getMessage().contains("SN-404"));
    }

    @Test
    void staleAndInvalidRevalidationBothBecomeDeviceAssignmentStale() {
        for (DeviceScopeRevalidationResult result : List.of(
                new DeviceScopeRevalidationResult(
                        DeviceScopeRevalidationResult.Decision.STALE, fact(5L, 6L), List.of()),
                new DeviceScopeRevalidationResult(
                        DeviceScopeRevalidationResult.Decision.INVALID, null,
                        List.of(new DeviceScopeInvalidItem(10L, "SN-1",
                                DeviceScopeInvalidItem.Reason.PROJECT_MISMATCH))))) {
            DeviceScopeFactApi api = mock(DeviceScopeFactApi.class);
            when(api.lockAndRevalidate(any())).thenReturn(result);
            DeviceScopeFactPort adapter = new DeviceScopeFactApiAdapter(api);

            OwnerFactVersionMismatchException exception = assertThrows(
                    OwnerFactVersionMismatchException.class,
                    () -> adapter.lockAndRevalidate(TENANT_ID, PROJECT_ID, expectedDevices()));

            assertEquals("AST", exception.ownerContext());
            assertEquals("DEVICE_ASSIGNMENT_STALE", exception.reasonCode());
        }
    }

    @Test
    void providerUnavailableMapsToRetryableOwnerFailureAndPreservesCause() {
        DeviceScopeFactApi api = mock(DeviceScopeFactApi.class);
        DeviceScopeFactException cause = new DeviceScopeFactException(
                DeviceScopeFactException.Code.PROVIDER_UNAVAILABLE, "AST unavailable");
        when(api.resolveBySerials(any())).thenThrow(cause);
        DeviceScopeFactPort adapter = new DeviceScopeFactApiAdapter(api);

        ArrivalAcceptanceContractException exception = assertThrows(
                ArrivalAcceptanceContractException.class,
                () -> adapter.resolveBySerials(TENANT_ID, PROJECT_ID, Set.of("SN-1")));

        assertEquals("OWNER_PROVIDER_UNAVAILABLE", exception.category());
        assertEquals("AST_PROVIDER_UNAVAILABLE", exception.reasonCode());
        assertEquals("AST", exception.ownerContext());
        assertSame(cause, exception.getCause());
    }

    @Test
    void consumerInputAndOwnerCorruptionRemainInternalFailuresWithPublicCause() {
        for (DeviceScopeFactException.Code code : List.of(
                DeviceScopeFactException.Code.INVALID_REQUEST,
                DeviceScopeFactException.Code.DUPLICATE_SERIAL,
                DeviceScopeFactException.Code.TENANT_CONTEXT_MISMATCH,
                DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED)) {
            DeviceScopeFactApi api = mock(DeviceScopeFactApi.class);
            DeviceScopeFactException cause = new DeviceScopeFactException(code, "contract failure");
            when(api.resolveBySerials(any())).thenThrow(cause);
            DeviceScopeFactPort adapter = new DeviceScopeFactApiAdapter(api);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> adapter.resolveBySerials(TENANT_ID, PROJECT_ID, Set.of("SN-1")));

            assertSame(cause, exception.getCause());
        }
    }

    @Test
    void mismatchedResolvedIdentityIsOwnerCorruptionRatherThanRecoverableFailure() {
        DeviceScopeFactApi api = mock(DeviceScopeFactApi.class);
        DeviceScopeFact wrongTenant = new DeviceScopeFact(2L, PROJECT_ID, fact().devices(),
                fact().scopeWatermark());
        when(api.resolveBySerials(any())).thenReturn(new DeviceScopeResolutionResult(
                DeviceScopeResolutionResult.Decision.RESOLVED, wrongTenant, List.of()));
        DeviceScopeFactPort adapter = new DeviceScopeFactApiAdapter(api);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> adapter.resolveBySerials(TENANT_ID, PROJECT_ID, Set.of("SN-1", "SN-2")));

        DeviceScopeFactException cause = assertInstanceOf(DeviceScopeFactException.class, exception.getCause());
        assertEquals(DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED, cause.getCode());
    }

    @Test
    void rejectsEmptyInputsWithoutCallingTheAstApi() {
        DeviceScopeFactApi api = mock(DeviceScopeFactApi.class);
        DeviceScopeFactPort adapter = new DeviceScopeFactApiAdapter(api);

        for (IllegalStateException exception : List.of(
                assertThrows(IllegalStateException.class,
                        () -> adapter.resolveBySerials(TENANT_ID, PROJECT_ID, Set.of())),
                assertThrows(IllegalStateException.class,
                        () -> adapter.lockAndRevalidate(TENANT_ID, PROJECT_ID, List.of())))) {
            DeviceScopeFactException cause = assertInstanceOf(
                    DeviceScopeFactException.class, exception.getCause());
            assertEquals(DeviceScopeFactException.Code.INVALID_REQUEST, cause.getCode());
        }
        verifyNoInteractions(api);
    }

    private static List<DeviceScopeFactPort.ExpectedDeviceFact> expectedDevices() {
        return List.of(
                new DeviceScopeFactPort.ExpectedDeviceFact(10L, "SN-1", 3L),
                new DeviceScopeFactPort.ExpectedDeviceFact(20L, "SN-2", 4L));
    }

    private static DeviceScopeFact fact() {
        return fact(3L, 4L);
    }

    private static DeviceScopeFact fact(Long firstVersion, Long secondVersion) {
        List<DeviceScopeFact.Device> devices = List.of(
                new DeviceScopeFact.Device(10L, "SN-1", PROJECT_ID, firstVersion),
                new DeviceScopeFact.Device(20L, "SN-2", PROJECT_ID, secondVersion));
        return new DeviceScopeFact(TENANT_ID, PROJECT_ID, devices,
                new DeviceScopeFact.ScopeWatermark(List.of(
                        new DeviceScopeFact.WatermarkEntry(10L, firstVersion),
                        new DeviceScopeFact.WatermarkEntry(20L, secondVersion))));
    }
}
