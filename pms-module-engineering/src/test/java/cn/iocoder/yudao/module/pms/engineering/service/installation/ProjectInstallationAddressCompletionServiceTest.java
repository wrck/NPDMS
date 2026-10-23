package cn.iocoder.yudao.module.pms.engineering.service.installation;
import cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationFact;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectInstallationAddressCompletionServiceTest {
    final DeviceQueryApi devices = mock(DeviceQueryApi.class);
    final InstallationMapper installations = mock(InstallationMapper.class);
    final ProjectInstallationAddressCompletionService service = new ProjectInstallationAddressCompletionService(devices, installations);
    final InstallationAddressCompletionApi.Query query = new InstallationAddressCompletionApi.Query(1L, 9L);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); when(installations.selectProjectAddressRecordsForUpdate(any())).thenReturn(List.of()); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void emptyInventoryAndMissingOrBlankAddressesCannotComplete() {
        when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of());
        assertFalse(service.lockAndInspect(query).completed());
        when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of(new ProjectDeviceInstallationFact(10L, "A", " "), new ProjectDeviceInstallationFact(11L, "B", "地址")));
        assertEquals(1, service.lockAndInspect(query).addressedDevices());
        assertFalse(service.lockAndInspect(query).completed());
        var record = new InstallationDO(); record.setEquipmentId(10L); record.setInstallLocation("新增地址"); record.setStatus(0);
        when(installations.selectProjectAddressRecordsForUpdate(any())).thenReturn(List.of(record));
        assertTrue(service.lockAndInspect(query).completed());
        assertEquals(0, record.getStatus());
    }
    @Test void anotherDeviceRecordDoesNotCoverMissingDeviceAndTenantMismatchIsRejected() {
        when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of(new ProjectDeviceInstallationFact(10L, "A", null)));
        var record = new InstallationDO(); record.setEquipmentId(11L); record.setInstallLocation("其他设备地址");
        when(installations.selectProjectAddressRecordsForUpdate(any())).thenReturn(List.of(record));
        assertFalse(service.lockAndInspect(query).completed());
        assertThrows(IllegalArgumentException.class, () -> service.lockAndInspect(new InstallationAddressCompletionApi.Query(2L, 9L)));
    }
}
