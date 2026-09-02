package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query;

public record ProductTypeSourceMappingLockQuery(
        Long tenantId,
        String sourceSystem,
        String sourceKey) {
}
