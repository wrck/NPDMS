package cn.iocoder.yudao.module.pms.project.service.projectgovernance.command;

public record ReopenProjectCommand(
        Long projectId,
        Integer expectedVersion,
        String reasonCode,
        String reasonDetail,
        Long exceptionCloseSnapshotId,
        String idempotencyKey,
        String requestDigest) {
}
