package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeFact;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeInvalidItem;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolutionResult;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceScopeFactApiContractTest {

    @Test
    void apiExposesOnlyTheTwoLockedTypedOperations() {
        List<Method> methods = Arrays.stream(DeviceScopeFactApi.class.getDeclaredMethods())
                .sorted(java.util.Comparator.comparing(Method::getName)).toList();
        assertEquals(List.of("lockAndRevalidate", "resolveBySerials"),
                methods.stream().map(Method::getName).toList());
        assertEquals(DeviceScopeRevalidationQuery.class, methods.get(0).getParameterTypes()[0]);
        assertEquals(DeviceScopeRevalidationResult.class, methods.get(0).getReturnType());
        assertEquals(DeviceScopeResolveQuery.class, methods.get(1).getParameterTypes()[0]);
        assertEquals(DeviceScopeResolutionResult.class, methods.get(1).getReturnType());
    }

    @Test
    void serialInputTrimsAndRejectsCaseInsensitiveDuplicates() {
        DeviceScopeResolveQuery query = new DeviceScopeResolveQuery(1L, 10L, List.of(" sn-b ", "SN-A"));
        assertEquals(List.of("sn-b", "SN-A"), query.serialNumbers());
        assertEquals("SN-B", DeviceScopeResolveQuery.comparisonKey(query.serialNumbers().getFirst()));

        DeviceScopeFactException duplicate = assertThrows(DeviceScopeFactException.class,
                () -> new DeviceScopeResolveQuery(1L, 10L, List.of(" sn-a ", "SN-A")));
        assertEquals(DeviceScopeFactException.Code.DUPLICATE_SERIAL, duplicate.getCode());
        DeviceScopeFactException empty = assertThrows(DeviceScopeFactException.class,
                () -> new DeviceScopeResolveQuery(1L, 10L, List.of()));
        assertEquals(DeviceScopeFactException.Code.INVALID_REQUEST, empty.getCode());
    }

    @Test
    void factOrdersDevicesAndUsesOnlyTheMatchingAssignmentVector() {
        DeviceScopeFact fact = new DeviceScopeFact(1L, 10L, List.of(
                new DeviceScopeFact.Device(22L, "SN-22", 10L, 8L),
                new DeviceScopeFact.Device(11L, "SN-11", 10L, 7L)),
                new DeviceScopeFact.ScopeWatermark(List.of(
                        new DeviceScopeFact.WatermarkEntry(22L, 8L),
                        new DeviceScopeFact.WatermarkEntry(11L, 7L))));

        assertEquals(List.of(11L, 22L), fact.devices().stream().map(DeviceScopeFact.Device::deviceId).toList());
        assertEquals(List.of(11L, 22L), fact.scopeWatermark().entries().stream()
                .map(DeviceScopeFact.WatermarkEntry::deviceId).toList());
        DeviceScopeFactException mismatch = assertThrows(DeviceScopeFactException.class,
                () -> new DeviceScopeFact(1L, 10L, fact.devices(),
                        new DeviceScopeFact.ScopeWatermark(List.of(
                                new DeviceScopeFact.WatermarkEntry(11L, 7L),
                                new DeviceScopeFact.WatermarkEntry(22L, 9L)))));
        assertEquals(DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED, mismatch.getCode());
    }

    @Test
    void revalidationExpectationMustDescribeOneCompleteConsistentSet() {
        List<DeviceScopeRevalidationQuery.ExpectedDevice> devices = List.of(
                new DeviceScopeRevalidationQuery.ExpectedDevice(22L, "SN-22", 8L),
                new DeviceScopeRevalidationQuery.ExpectedDevice(11L, "SN-11", 7L));
        DeviceScopeRevalidationQuery query = new DeviceScopeRevalidationQuery(1L, 10L, devices,
                new DeviceScopeFact.ScopeWatermark(List.of(
                        new DeviceScopeFact.WatermarkEntry(11L, 7L),
                        new DeviceScopeFact.WatermarkEntry(22L, 8L))));
        assertEquals(List.of(11L, 22L), query.expectedDevices().stream()
                .map(DeviceScopeRevalidationQuery.ExpectedDevice::deviceId).toList());

        DeviceScopeFactException mismatch = assertThrows(DeviceScopeFactException.class,
                () -> new DeviceScopeRevalidationQuery(1L, 10L, devices,
                        new DeviceScopeFact.ScopeWatermark(List.of(
                                new DeviceScopeFact.WatermarkEntry(11L, 7L),
                                new DeviceScopeFact.WatermarkEntry(22L, 9L)))));
        assertEquals(DeviceScopeFactException.Code.INVALID_REQUEST, mismatch.getCode());
    }

    @Test
    void invalidAndStaleRemainDistinctWithoutPartialFacts() {
        DeviceScopeFact fact = fact();
        DeviceScopeRevalidationResult stale = new DeviceScopeRevalidationResult(
                DeviceScopeRevalidationResult.Decision.STALE, fact, List.of());
        assertEquals(fact, stale.currentFact());

        DeviceScopeRevalidationResult invalid = new DeviceScopeRevalidationResult(
                DeviceScopeRevalidationResult.Decision.INVALID, null,
                List.of(new DeviceScopeInvalidItem(11L, "SN-11",
                        DeviceScopeInvalidItem.Reason.PROJECT_MISMATCH)));
        assertNull(invalid.currentFact());
        assertEquals(DeviceScopeInvalidItem.Reason.PROJECT_MISMATCH, invalid.invalidItems().getFirst().reason());

        assertThrows(DeviceScopeFactException.class, () -> new DeviceScopeRevalidationResult(
                DeviceScopeRevalidationResult.Decision.INVALID, fact, invalid.invalidItems()));
        assertThrows(DeviceScopeFactException.class, () -> new DeviceScopeResolutionResult(
                DeviceScopeResolutionResult.Decision.INVALID, null, List.of()));
    }

    @Test
    void publicFailureCodesAreClosedAndDoNotExposeConsumerTypes() {
        assertEquals(List.of("DUPLICATE_SERIAL", "INVALID_REQUEST", "OWNER_DATA_CORRUPTED",
                        "PROVIDER_UNAVAILABLE", "TENANT_CONTEXT_MISMATCH"),
                Arrays.stream(DeviceScopeFactException.Code.values()).map(Enum::name).sorted().toList());
    }

    private static DeviceScopeFact fact() {
        return new DeviceScopeFact(1L, 10L,
                List.of(new DeviceScopeFact.Device(11L, "SN-11", 10L, 7L)),
                new DeviceScopeFact.ScopeWatermark(
                        List.of(new DeviceScopeFact.WatermarkEntry(11L, 7L))));
    }
}
