package cn.iocoder.yudao.module.pms.platform.api.export;

public record ExportTaskRetryCommand(
        Long tenantId,
        Long actorUserId,
        Long taskId,
        Integer expectedVersion) {
}
