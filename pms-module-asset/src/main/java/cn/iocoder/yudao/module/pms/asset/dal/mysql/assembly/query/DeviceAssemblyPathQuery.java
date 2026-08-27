package cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query;

public record DeviceAssemblyPathQuery(
        Long tenantId,
        String ancestorDeviceSn,
        String descendantDeviceSn) {
}
