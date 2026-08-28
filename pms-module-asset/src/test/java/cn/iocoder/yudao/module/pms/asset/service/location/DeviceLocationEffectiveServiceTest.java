package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.DeviceLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.DeviceLocationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.query.DeviceLocationProjectionUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_LOCATION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceLocationEffectiveServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceLocationMapper locationMapper;
    private DeviceLocationEffectiveService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new DeviceLocationEffectiveService(deviceMapper, locationMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCloseCurrentFactAndProjectNewLocation() {
        DeviceDO device = device();
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device);
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(null);
        when(deviceMapper.updateLocationProjection(any(DeviceLocationProjectionUpdate.class))).thenReturn(1);

        assertTrue(service.effect(command()));

        verify(locationMapper).closeCurrent(1L, "SN-8", command().effectiveFrom());
        verify(locationMapper).insert(argThat((DeviceLocationDO value) ->
                "SN-8".equals(value.getDeviceSn()) && Long.valueOf(201L).equals(value.getInstallationId())));
        verify(deviceMapper).updateLocationProjection(argThat((DeviceLocationProjectionUpdate value) ->
                Long.valueOf(21L).equals(value.siteId())
                        && Long.valueOf(31L).equals(value.siteLocationId())
                        && "RESOLVED".equals(value.resolutionStatus())));
    }

    @Test
    void shouldReplaySameInstallationWithoutSecondWrite() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device());
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(location("SN-8", 21L, 31L));

        assertTrue(service.effect(command()));

        verify(locationMapper, never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper, never()).updateLocationProjection(any(DeviceLocationProjectionUpdate.class));
    }

    @Test
    void shouldRejectSameInstallationWithDifferentRequest() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device());
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(location("SN-8", 22L, 31L));

        ServiceException error = assertThrows(ServiceException.class, () -> service.effect(command()));

        assertEquals(AST_EQUIPMENT_LOCATION_CONFLICT.getCode(), error.getCode());
        verify(locationMapper, never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper, never()).updateLocationProjection(any(DeviceLocationProjectionUpdate.class));
    }

    @Test
    void shouldRejectInstallationAlreadyUsedByAnotherDevice() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device());
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(location("SN-9", 21L, 31L));

        ServiceException error = assertThrows(ServiceException.class, () -> service.effect(command()));

        assertEquals(AST_EQUIPMENT_LOCATION_CONFLICT.getCode(), error.getCode());
        verify(locationMapper, never()).insert(any(DeviceLocationDO.class));
        verify(deviceMapper, never()).updateLocationProjection(any(DeviceLocationProjectionUpdate.class));
    }

    @Test
    void shouldRejectStaleDeviceVersionWhenProjectionCasFails() {
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 8L)).thenReturn(device());
        when(locationMapper.selectByTenantAndInstallation(1L, 201L)).thenReturn(null);
        when(deviceMapper.updateLocationProjection(any(DeviceLocationProjectionUpdate.class))).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class, () -> service.effect(command()));

        assertEquals(AST_EQUIPMENT_LOCATION_CONFLICT.getCode(), error.getCode());
    }

    private DeviceDO device() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        device.setVersion(2);
        return device;
    }

    private DeviceLocationDO location(String deviceSn, Long siteId, Long siteLocationId) {
        DeviceLocationDO location = new DeviceLocationDO();
        location.setTenantId(1L);
        location.setDeviceSn(deviceSn);
        location.setSiteId(siteId);
        location.setSiteLocationId(siteLocationId);
        location.setResolutionStatus("RESOLVED");
        location.setLocationSnapshot("snapshot");
        location.setEffectiveFrom(command().effectiveFrom());
        location.setInstallationId(201L);
        return location;
    }

    private EquipmentLocationEffectiveCommand command() {
        return new EquipmentLocationEffectiveCommand(
                8L, 201L, 21L, 31L, "机柜A", "RESOLVED", "snapshot",
                LocalDateTime.of(2026, 8, 27, 15, 30));
    }
}
