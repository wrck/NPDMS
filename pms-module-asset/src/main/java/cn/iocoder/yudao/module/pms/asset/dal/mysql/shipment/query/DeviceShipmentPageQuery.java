package cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query;
public record DeviceShipmentPageQuery(Long tenantId,String deviceSn,int pageNo,int pageSize) {}
