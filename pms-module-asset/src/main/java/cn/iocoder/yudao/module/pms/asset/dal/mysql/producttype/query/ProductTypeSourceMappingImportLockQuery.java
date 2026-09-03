package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query;

public record ProductTypeSourceMappingImportLockQuery(
        Long tenantId,
        String sourceSystem,
        String sourceKey) {
}
