package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record FileUploadSessionTerminationUpdate(
        Long tenantId,
        Long sessionId,
        Integer expectedVersion,
        Long actorUserId,
        String reasonCode) {
}
