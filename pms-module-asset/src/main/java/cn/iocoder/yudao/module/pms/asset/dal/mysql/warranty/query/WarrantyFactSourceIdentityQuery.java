package cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.query;

public record WarrantyFactSourceIdentityQuery(Long tenantId, String sourceTable, String sourceKey, String sourceChecksum) {}
