package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipmentconfiglog.EquipmentConfigLogDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipmentconfiglog.EquipmentConfigLogMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipmentconfiglog.query.DeviceConfigurationLogListQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceConfigurationLogQueryServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private EquipmentConfigLogMapper configurationLogMapper;
    @Mock private PermissionApi permissionApi;
    private DeviceConfigurationLogQueryService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new DeviceConfigurationLogQueryService(deviceMapper, configurationLogMapper, permissionApi);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnOnlySafeMetadataAndDownloadCapability() {
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        when(configurationLogMapper.selectList(new DeviceConfigurationLogListQuery(1L, 8L)))
                .thenReturn(List.of(configurationLog()));
        when(permissionApi.hasAnyPermissions(7L, DeviceConfigurationLogQueryService.DOWNLOAD_PERMISSION))
                .thenReturn(true);

        List<DeviceConfigurationLogMetadata> result = service.getList(1L, 7L, 8L);

        assertEquals(1, result.size());
        DeviceConfigurationLogMetadata metadata = result.getFirst();
        assertEquals(21L, metadata.id());
        assertEquals("RUNNING_CONFIG", metadata.configType());
        assertEquals("NMS", metadata.sourceSystem());
        assertEquals("abc123", metadata.fileHash());
        assertTrue(metadata.downloadable());
        Set<String> fields = java.util.Arrays.stream(DeviceConfigurationLogMetadata.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertFalse(fields.contains("configContent"));
        assertFalse(fields.contains("fileUrl"));
        assertFalse(fields.contains("downloadUrl"));
    }

    @Test
    void shouldHideDownloadCapabilityWithoutFilePermission() {
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        when(configurationLogMapper.selectList(new DeviceConfigurationLogListQuery(1L, 8L)))
                .thenReturn(List.of(configurationLog()));
        when(permissionApi.hasAnyPermissions(7L, DeviceConfigurationLogQueryService.DOWNLOAD_PERMISSION))
                .thenReturn(false);

        List<DeviceConfigurationLogMetadata> result = service.getList(1L, 7L, 8L);

        assertFalse(result.getFirst().downloadable());
    }

    @Test
    void shouldRejectInvisibleDeviceBeforeReadingLogs() {
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.getList(1L, 7L, 8L));

        assertEquals(AST_EQUIPMENT_NOT_EXISTS.getCode(), error.getCode());
        verify(configurationLogMapper, never()).selectList(new DeviceConfigurationLogListQuery(1L, 8L));
    }

    @Test
    void shouldRejectCrossTenantQueryBeforeReadingDevice() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.getList(2L, 7L, 8L));

        assertEquals(AST_EQUIPMENT_NOT_EXISTS.getCode(), error.getCode());
        verify(deviceMapper, never()).selectByTenantAndId(2L, 8L);
    }

    private DeviceDO device() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setSn("SN-8");
        device.setTenantId(1L);
        return device;
    }

    private EquipmentConfigLogDO configurationLog() {
        EquipmentConfigLogDO log = new EquipmentConfigLogDO();
        log.setId(21L);
        log.setEquipmentId(8L);
        log.setConfigType("RUNNING_CONFIG");
        log.setConfigContent("secret configuration");
        log.setSourceSystem("NMS");
        log.setCollectedAt(LocalDateTime.of(2026, 8, 27, 10, 0));
        log.setFileUrl("https://storage.example/config/21.txt");
        log.setFileHash("abc123");
        log.setRemark("巡检采集");
        log.setTenantId(1L);
        return log;
    }
}
