package cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.ShipmentReferenceRecordQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShipmentReferenceCorrectionMapper {
    DeviceShipmentDO selectShipmentForUpdate(@Param("query") ShipmentReferenceRecordQuery query);
    DeviceDO selectDeviceForUpdate(@Param("query") ShipmentReferenceRecordQuery query);
    record ShipmentWrite(Long tenantId,Long id,Long orderLineId,Integer version) {}
    record DeviceWrite(Long tenantId,Long id,Long shipmentId,Long previousShipmentId,Long version) {}
    int fillOrderLine(@Param("command") ShipmentWrite command);
    int repairDeviceShipment(@Param("command") DeviceWrite command);
}
