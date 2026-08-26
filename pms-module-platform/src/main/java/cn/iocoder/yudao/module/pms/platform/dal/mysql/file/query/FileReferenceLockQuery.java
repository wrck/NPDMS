package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record FileReferenceLockQuery(
        Long tenantId,
        String ownerContext,
        String objectType,
        String objectId,
        String purposeCode,
        String referenceKey) {
}
