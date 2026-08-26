package cn.iocoder.yudao.module.pms.platform.service.file.command;

public record FileUploadTerminateCommand(
        Long tenantId,
        Long actorUserId,
        Long sessionId,
        String reasonCode) {
}
