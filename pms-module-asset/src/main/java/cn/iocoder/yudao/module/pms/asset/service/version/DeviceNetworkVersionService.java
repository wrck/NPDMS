package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionEventDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceNetworkVersionEventMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceNetworkVersionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceNetworkVersionSourceQuery;
import cn.iocoder.yudao.module.pms.asset.service.version.command.ApplyDeviceNetworkVersionCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceNetworkVersionService {

    private final DeviceMapper deviceMapper;
    private final DeviceNetworkVersionMapper versionMapper;
    private final DeviceNetworkVersionEventMapper eventMapper;

    @Transactional(rollbackFor = Exception.class)
    public void apply(ApplyDeviceNetworkVersionCommand command) {
        DeviceNetworkVersionSourceQuery sourceQuery = new DeviceNetworkVersionSourceQuery(
                command.tenantId(), command.sourceSystem(), command.sourceDeviceKey(), command.sourceEventKey());
        if (eventMapper.existsBySource(sourceQuery)) {
            return;
        }
        DeviceDO device = deviceMapper.selectByTenantAndSn(command.tenantId(), command.deviceSn());
        if (device == null) {
            throw new IllegalArgumentException("设备不存在");
        }

        DeviceNetworkVersionEventDO event = toEvent(command);
        eventMapper.insert(event);

        DeviceNetworkVersionDO current = versionMapper.selectByTenantAndSn(command.tenantId(), command.deviceSn());
        if (current != null && current.getEffectiveFrom() != null
                && current.getEffectiveFrom().isAfter(command.eventTime())) {
            return;
        }

        DeviceNetworkVersionDO version = toVersion(command, current == null ? null : current.getId());
        if (current == null) {
            versionMapper.insert(version);
        } else {
            versionMapper.updateById(version);
        }

        DeviceDO projection = new DeviceDO();
        projection.setId(device.getId());
        projection.setConpVersion(command.conpVersion());
        projection.setConpType(command.conpType());
        projection.setConpSeries(command.conpSeries());
        projection.setConpMark(command.conpMark());
        deviceMapper.updateById(projection);
    }

    private DeviceNetworkVersionEventDO toEvent(ApplyDeviceNetworkVersionCommand command) {
        DeviceNetworkVersionEventDO event = new DeviceNetworkVersionEventDO();
        event.setTenantId(command.tenantId());
        event.setDeviceSn(command.deviceSn());
        event.setSourceDeviceKey(command.sourceDeviceKey());
        event.setSourceEventKey(command.sourceEventKey());
        copyVersion(command, event);
        event.setEventTime(command.eventTime());
        event.setReceivedTime(command.syncedAt());
        event.setRevoked(false);
        event.setMappingStatus("MAPPED");
        event.setSourceSystem(command.sourceSystem());
        event.setSourceVersion(command.sourceVersion());
        event.setSyncStatus(command.syncStatus());
        return event;
    }

    private DeviceNetworkVersionDO toVersion(ApplyDeviceNetworkVersionCommand command, Long id) {
        DeviceNetworkVersionDO version = new DeviceNetworkVersionDO();
        version.setId(id);
        version.setTenantId(command.tenantId());
        version.setDeviceSn(command.deviceSn());
        version.setConpVersion(command.conpVersion());
        version.setConpType(command.conpType());
        version.setConpSeries(command.conpSeries());
        version.setConpMark(command.conpMark());
        version.setBootVersion(command.bootVersion());
        version.setCpldVersion(command.cpldVersion());
        version.setPcbVersion(command.pcbVersion());
        version.setCustomized(command.customized());
        version.setEffectiveFrom(command.eventTime());
        version.setSourceSystem(command.sourceSystem());
        version.setSourceKey(command.sourceDeviceKey());
        version.setSourceVersion(command.sourceVersion());
        version.setSourceUpdatedAt(command.sourceUpdatedAt());
        version.setSyncedAt(command.syncedAt());
        version.setSyncStatus(command.syncStatus());
        return version;
    }

    private void copyVersion(ApplyDeviceNetworkVersionCommand command, DeviceNetworkVersionEventDO event) {
        event.setConpVersion(command.conpVersion());
        event.setConpType(command.conpType());
        event.setConpSeries(command.conpSeries());
        event.setConpMark(command.conpMark());
        event.setBootVersion(command.bootVersion());
        event.setCpldVersion(command.cpldVersion());
        event.setPcbVersion(command.pcbVersion());
        event.setCustomized(command.customized());
    }
}
