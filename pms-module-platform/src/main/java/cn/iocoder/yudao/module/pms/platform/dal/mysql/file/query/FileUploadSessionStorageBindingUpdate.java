package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record FileUploadSessionStorageBindingUpdate(
        Long tenantId, Long sessionId, Integer expectedVersion,
        String actualSha256, Long registeredInfraFileId) {
}
