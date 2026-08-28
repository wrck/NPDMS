package cn.iocoder.yudao.module.pms.asset.service.shipment;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.DeviceShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.DeviceShipmentSourceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.LatestDeviceShipmentQuery;
import cn.iocoder.yudao.module.pms.asset.service.shipment.command.ApplyDeviceShipmentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DeviceShipmentService {

    private final DeviceMapper deviceMapper;
    private final DeviceShipmentMapper shipmentMapper;

    @Transactional(rollbackFor = Exception.class)
    public void apply(ApplyDeviceShipmentCommand command) {
        DeviceShipmentSourceQuery sourceQuery = new DeviceShipmentSourceQuery(
                command.tenantId(), command.sourceSystem(), command.sourceKey());
        if (shipmentMapper.existsBySource(sourceQuery)) {
            return;
        }
        DeviceDO device = deviceMapper.selectByTenantAndSn(command.tenantId(), command.deviceSn());
        if (device == null) {
            throw new IllegalArgumentException("设备不存在");
        }

        DeviceShipmentDO shipment = new DeviceShipmentDO();
        shipment.setTenantId(command.tenantId());
        shipment.setDeviceSn(command.deviceSn());
        shipment.setShipmentTime(command.shipmentTime());
        shipment.setPackageNo(command.packageNo());
        shipment.setContractNo(command.contractNo());
        shipment.setEventType(command.eventType());
        shipment.setSourceSystem(command.sourceSystem());
        shipment.setSourceKey(command.sourceKey());
        shipment.setSourceVersion(command.sourceVersion());
        shipment.setSourceUpdatedAt(command.sourceUpdatedAt());
        shipment.setSyncedAt(command.syncedAt());
        shipment.setSyncStatus(command.syncStatus());
        shipmentMapper.insert(shipment);

        DeviceShipmentDO latest = shipmentMapper.selectLatest(
                new LatestDeviceShipmentQuery(command.tenantId(), command.deviceSn()));
        if (latest == null || !Objects.equals(latest.getId(), shipment.getId())) {
            return;
        }

        DeviceDO projection = new DeviceDO();
        projection.setId(device.getId());
        projection.setShipmentTime(latest.getShipmentTime());
        projection.setPackageNo(latest.getPackageNo());
        projection.setContractNo(latest.getContractNo());
        projection.setShipmentRecordId(latest.getId());
        deviceMapper.updateById(projection);
    }
}
