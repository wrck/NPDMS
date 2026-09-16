package cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query;

public record EntityDefinitionSourceQuery(Long tenantId, String ownerModule, String entityType,
                                          Long sourceFormRevisionId) {}
