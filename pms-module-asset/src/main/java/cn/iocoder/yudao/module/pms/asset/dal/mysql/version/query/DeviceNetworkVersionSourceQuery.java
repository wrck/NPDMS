package cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query;

public record DeviceNetworkVersionSourceQuery(
        Long tenantId,
        String sourceSystem,
        String sourceDeviceKey,
        String sourceEventKey) {
}
