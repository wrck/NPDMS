package cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query;

public record DeviceAssemblySourceQuery(
        Long tenantId,
        String sourceSystem,
        String sourceKey) {
}
