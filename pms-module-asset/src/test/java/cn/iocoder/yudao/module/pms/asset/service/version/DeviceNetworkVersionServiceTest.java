package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionEventDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceNetworkVersionEventMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceNetworkVersionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceNetworkVersionSourceQuery;
import cn.iocoder.yudao.module.pms.asset.service.version.command.ApplyDeviceNetworkVersionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceNetworkVersionServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceNetworkVersionMapper versionMapper;
    @Mock private DeviceNetworkVersionEventMapper eventMapper;
    private DeviceNetworkVersionService service;

    @BeforeEach
    void setUp() {
        service = new DeviceNetworkVersionService(deviceMapper, versionMapper, eventMapper);
    }

    @Test
    void shouldPersistCompleteVersionAndUpdateConpProjectionFromSameEvent() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        when(deviceMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(device);

        service.apply(command("event-2", LocalDateTime.of(2026, 8, 26, 20, 0), "V3.2.1"));

        verify(eventMapper).insert(argThat((DeviceNetworkVersionEventDO event) ->
                "V3.2.1".equals(event.getConpVersion())
                        && "B1".equals(event.getBootVersion()) && "C1".equals(event.getCpldVersion())
                        && "P1".equals(event.getPcbVersion())));
        verify(versionMapper).insert(argThat((DeviceNetworkVersionDO version) ->
                "V3.2.1".equals(version.getConpVersion()) && "3.2.1".equals(version.getConpMark())));
        verify(deviceMapper).updateById(argThat((DeviceDO update) ->
                "V3.2.1".equals(update.getConpVersion())
                        && "CONP".equals(update.getConpType()) && "S3".equals(update.getConpSeries())
                        && "3.2.1".equals(update.getConpMark())));
    }

    @Test
    void shouldKeepCurrentFactAndProjectionWhenEventIsOlder() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        when(deviceMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(device);
        DeviceNetworkVersionDO current = new DeviceNetworkVersionDO();
        current.setId(9L);
        current.setEffectiveFrom(LocalDateTime.of(2026, 8, 26, 20, 0));
        when(versionMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(current);

        service.apply(command("event-1", LocalDateTime.of(2026, 8, 26, 19, 0), "V3.1.9"));

        verify(eventMapper).insert(any(DeviceNetworkVersionEventDO.class));
        verify(versionMapper, never()).updateById(any(DeviceNetworkVersionDO.class));
        verify(versionMapper, never()).insert(any(DeviceNetworkVersionDO.class));
        verify(deviceMapper, never()).updateById(any(DeviceDO.class));
    }

    @Test
    void shouldIgnoreDuplicateSourceEvent() {
        when(eventMapper.existsBySource(any(DeviceNetworkVersionSourceQuery.class))).thenReturn(true);

        service.apply(command("event-2", LocalDateTime.of(2026, 8, 26, 20, 0), "V3.2.1"));

        verifyNoInteractions(deviceMapper, versionMapper);
        verify(eventMapper, never()).insert(any(DeviceNetworkVersionEventDO.class));
    }

    private ApplyDeviceNetworkVersionCommand command(String eventKey, LocalDateTime eventTime, String conpVersion) {
        return new ApplyDeviceNetworkVersionCommand(
                1L, "SN-8", "source-device-8", eventKey, eventTime,
                conpVersion, "CONP", "S3", "3.2.1", "B1", "C1", "P1", false,
                "ITR", "2", eventTime, LocalDateTime.of(2026, 8, 26, 20, 5), "FRESH");
    }
}
