package cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query;

public record DeviceShipmentSourceQuery(
        Long tenantId,
        String sourceSystem,
        String sourceKey) {
}
