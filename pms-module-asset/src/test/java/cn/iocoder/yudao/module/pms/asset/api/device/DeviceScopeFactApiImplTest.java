package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeFact;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationResult;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceScopeFactApiImplTest {

    @Mock private DeviceMapper deviceMapper;
    @InjectMocks private DeviceScopeFactApiImpl api;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void resolvesCompleteFactInStableDeviceOrder() {
        TenantContextHolder.setTenantId(1L);
        when(deviceMapper.selectListByScopeSerials(any())).thenReturn(List.of(
                device(22L, "sn-b", "ACTIVE", 10L, 8L),
                device(11L, "SN-A", "IN_USE", 10L, 7L)));

        var result = api.resolveBySerials(new DeviceScopeResolveQuery(1L, 10L, List.of("SN-B", "sn-a")));

        assertEquals("RESOLVED", result.decision().name());
        assertEquals(List.of(11L, 22L), result.fact().devices().stream()
                .map(DeviceScopeFact.Device::deviceId).toList());
        assertEquals(List.of(7L, 8L), result.fact().scopeWatermark().entries().stream()
                .map(DeviceScopeFact.WatermarkEntry::projectAssignmentVersion).toList());
    }

    @Test
    void resolutionReturnsAllInvalidReasonsWithoutPartialFact() {
        TenantContextHolder.setTenantId(1L);
        when(deviceMapper.selectListByScopeSerials(any())).thenReturn(List.of(
                device(11L, "SN-STATUS", "RETIRED", 10L, 1L),
                device(22L, "SN-PROJECT", "ACTIVE", 99L, 2L)));

        var result = api.resolveBySerials(new DeviceScopeResolveQuery(1L, 10L,
                List.of("SN-MISSING", "SN-STATUS", "SN-PROJECT")));

        assertEquals("INVALID", result.decision().name());
        assertNull(result.fact());
        assertEquals(List.of("STATUS_INELIGIBLE", "PROJECT_MISMATCH", "NOT_FOUND"),
                result.invalidItems().stream().map(item -> item.reason().name()).toList());
    }

    @Test
    void tenantMismatchFailsBeforeOwnerRead() {
        TenantContextHolder.setTenantId(2L);

        DeviceScopeFactException failure = assertThrows(DeviceScopeFactException.class,
                () -> api.resolveBySerials(new DeviceScopeResolveQuery(1L, 10L, List.of("SN-A"))));

        assertEquals(DeviceScopeFactException.Code.TENANT_CONTEXT_MISMATCH, failure.getCode());
        verify(deviceMapper, never()).selectListByScopeSerials(any());
    }

    @Test
    void revalidationReturnsStaleOnlyForAssignmentVersionChange() {
        TenantContextHolder.setTenantId(1L);
        when(deviceMapper.selectScopeDevicesForUpdate(any())).thenReturn(List.of(
                device(11L, "sn-a", "ACTIVE", 10L, 8L)));

        var result = api.lockAndRevalidate(revalidation("SN-A", 7L));

        assertEquals(DeviceScopeRevalidationResult.Decision.STALE, result.decision());
        assertEquals(8L, result.currentFact().devices().getFirst().projectAssignmentVersion());
    }

    @Test
    void revalidationRejectsChangedSerialIdentityAsOwnerCorruption() {
        TenantContextHolder.setTenantId(1L);
        when(deviceMapper.selectScopeDevicesForUpdate(any())).thenReturn(List.of(
                device(11L, "SN-OTHER", "ACTIVE", 10L, 7L)));

        DeviceScopeFactException failure = assertThrows(DeviceScopeFactException.class,
                () -> api.lockAndRevalidate(revalidation("SN-A", 7L)));

        assertEquals(DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED, failure.getCode());
    }

    @Test
    void ownerReadFailureUsesStableProviderUnavailableCode() {
        TenantContextHolder.setTenantId(1L);
        when(deviceMapper.selectListByScopeSerials(any()))
                .thenThrow(new DataAccessResourceFailureException("offline"));

        DeviceScopeFactException failure = assertThrows(DeviceScopeFactException.class,
                () -> api.resolveBySerials(new DeviceScopeResolveQuery(1L, 10L, List.of("SN-A"))));

        assertEquals(DeviceScopeFactException.Code.PROVIDER_UNAVAILABLE, failure.getCode());
    }

    private static DeviceScopeRevalidationQuery revalidation(String serialNumber, long version) {
        return new DeviceScopeRevalidationQuery(1L, 10L,
                List.of(new DeviceScopeRevalidationQuery.ExpectedDevice(11L, serialNumber, version)),
                new DeviceScopeRevalidationQuery.ExpectedScopeWatermark(List.of(
                        new DeviceScopeRevalidationQuery.ExpectedWatermarkEntry(11L, version))));
    }

    private static DeviceDO device(long id, String serialNumber, String status,
                                   Long projectId, long assignmentVersion) {
        DeviceDO device = new DeviceDO();
        device.setId(id);
        device.setTenantId(1L);
        device.setSn(serialNumber);
        device.setStatus(status);
        device.setProjectId(projectId);
        device.setProjectAssignmentVersion(assignmentVersion);
        return device;
    }
}
