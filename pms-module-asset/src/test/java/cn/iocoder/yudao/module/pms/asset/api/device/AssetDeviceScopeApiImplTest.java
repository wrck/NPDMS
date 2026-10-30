package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.SerialScopeValidationResult;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetDeviceScopeApiImplTest {
    @Mock private DeviceMapper deviceMapper;
    @InjectMocks private AssetDeviceScopeApiImpl api;

    @Test
    void shouldReturnOnlyClassificationWithoutSensitiveDeviceDetails() {
        when(deviceMapper.selectListBySns(anyCollection())).thenReturn(List.of(
                device("SN-1", 1L, 100L, "IN_STOCK"),
                device("SN-2", 1L, 999L, "IN_USE"),
                device("SN-3", 1L, 100L, "RETIRED")));

        SerialScopeValidationResult result = api.validateAssignableSerials(1L, 100L,
                List.of("SN-1", "SN-2", "SN-3", "SN-4", "SN-1"));

        assertFalse(result.valid());
        assertEquals(List.of("SN-4"), result.missingSerialNumbers());
        assertEquals(List.of("SN-2", "SN-3"), result.unavailableSerialNumbers());
        assertEquals(List.of("SN-1"), result.duplicateSerialNumbers());
    }

    private DeviceDO device(String sn, Long tenantId, Long projectId, String status) {
        DeviceDO device = new DeviceDO();
        device.setSn(sn);
        device.setTenantId(tenantId);
        device.setProjectId(projectId);
        device.setStatus(status);
        return device;
    }
}
