package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record FileUploadSessionValidationUpdate(
        Long tenantId,
        Long sessionId,
        Integer expectedVersion) {
}
