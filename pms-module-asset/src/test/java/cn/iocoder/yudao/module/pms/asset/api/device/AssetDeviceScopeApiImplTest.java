package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.SerialScopeValidationResult;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
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
    @Mock private EquipmentMapper equipmentMapper;
    @InjectMocks private AssetDeviceScopeApiImpl api;

    @Test
    void shouldReturnOnlyClassificationWithoutSensitiveDeviceDetails() {
        when(equipmentMapper.selectListBySerialNumbers(anyCollection())).thenReturn(List.of(
                equipment("SN-1", 1L, 100L, 0),
                equipment("SN-2", 1L, 999L, 1),
                equipment("SN-3", 1L, 100L, 4)));

        SerialScopeValidationResult result = api.validateAssignableSerials(1L, 100L,
                List.of("SN-1", "SN-2", "SN-3", "SN-4", "SN-1"));

        assertFalse(result.valid());
        assertEquals(List.of("SN-4"), result.missingSerialNumbers());
        assertEquals(List.of("SN-2", "SN-3"), result.unavailableSerialNumbers());
        assertEquals(List.of("SN-1"), result.duplicateSerialNumbers());
    }

    private EquipmentDO equipment(String serial, Long tenantId, Long projectId, Integer status) {
        EquipmentDO equipment = new EquipmentDO();
        equipment.setSerialNumber(serial);
        equipment.setTenantId(tenantId);
        equipment.setProjectId(projectId);
        equipment.setStatus(status);
        return equipment;
    }
}
