package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationFact;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApi;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.CutoverCoverageApi;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverCoverageServiceTest {
    private final DeviceQueryApi devices = mock(DeviceQueryApi.class);
    private final CutoverResultApi results = mock(CutoverResultApi.class);
    private final CutoverCoverageService service = new CutoverCoverageService(devices, results);
    private final CutoverCoverageApi.Query query = new CutoverCoverageApi.Query(1L, 10L);
    private final ProjectDeviceInstallationQuery range = new ProjectDeviceInstallationQuery(1L, 10L);

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void batchReadPreservesLockedRangeAssignmentAndSuccessCoverage() {
        var success = success();
        when(results.successes(new CutoverResultApi.ProjectQuery(1L, 10L))).thenReturn(List.of(success));
        when(devices.lockProjectInstallationDevices(range)).thenReturn(List.of(
                new ProjectDeviceInstallationFact(8L, "SN-8", ""), new ProjectDeviceInstallationFact(9L, "SN-9", "")));
        var summaries = List.of(summary(8L, 1L, 10L), summary(9L, 1L, 10L));
        when(devices.getProjectDeviceSummaries(range)).thenReturn(summaries);

        var coverage = service.inspect(query);
        assertEquals(List.of(8L), coverage.coveredDeviceIds());
        assertEquals(List.of(9L), coverage.missingDeviceIds());
        assertEquals(List.of(new CutoverCoverageApi.Device(8L, "SN-8", 2L),
                new CutoverCoverageApi.Device(9L, "SN-9", 2L)), coverage.requiredDevices());
        assertFalse(coverage.complete());
        verify(devices, never()).getDevice(anyLong());
        verify(results, never()).lockAndInspect(any());
    }

    @Test void lockedInspectionStillLocksEveryHistoricalSuccess() {
        var success = success();
        when(results.successes(new CutoverResultApi.ProjectQuery(1L, 10L))).thenReturn(List.of(success));
        when(results.lockAndInspect(new CutoverResultApi.ResultQuery(1L, 10L, 20L, 30L, 1L))).thenReturn(success);
        when(devices.lockProjectInstallationDevices(range)).thenReturn(List.of());
        assertFalse(service.lockAndInspect(query).complete());
        verify(results).lockAndInspect(new CutoverResultApi.ResultQuery(1L, 10L, 20L, 30L, 1L));
        verify(devices, never()).getProjectDeviceSummaries(any());
    }

    @Test void missingOrForeignDeviceCannotBeTreatedAsCovered() {
        when(results.successes(any())).thenReturn(List.of());
        when(devices.lockProjectInstallationDevices(range)).thenReturn(List.of(new ProjectDeviceInstallationFact(8L, "SN-8", "")));
        for (var batch : List.of(List.<DeviceSummaryDTO>of(), List.of(summary(8L, 2L, 10L)), List.of(summary(8L, 1L, 11L)))) {
            when(devices.getProjectDeviceSummaries(range)).thenReturn(batch);
            assertThrows(IllegalStateException.class, () -> service.inspect(query));
        }
    }

    @Test void foreignTenantFailsBeforeAnyOwnerRead() {
        assertThrows(SecurityException.class, () -> service.inspect(new CutoverCoverageApi.Query(2L, 10L)));
        verifyNoInteractions(devices, results);
    }

    private static DeviceSummaryDTO summary(Long id, Long tenant, Long project) {
        var row = mock(DeviceSummaryDTO.class);
        when(row.deviceId()).thenReturn(id); when(row.tenantId()).thenReturn(tenant);
        when(row.projectId()).thenReturn(project); when(row.sn()).thenReturn("SN-" + id);
        when(row.projectAssignmentVersion()).thenReturn(2L); return row;
    }

    private static CutoverResultApi.Result success() {
        return new CutoverResultApi.Result(1L, 10L, 20L, "CUT-20", "test", 30L, 1L, "result", 7L,
                java.time.LocalDateTime.of(2026, 10, 23, 1, 0),
                List.of(new CutoverResultApi.Device(8L, "sn-8", 2L)), List.of(), List.of());
    }
}
