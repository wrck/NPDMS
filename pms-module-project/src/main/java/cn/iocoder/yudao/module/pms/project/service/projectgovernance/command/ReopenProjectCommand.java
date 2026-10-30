package cn.iocoder.yudao.module.pms.project.service.projectgovernance.command;

public record ReopenProjectCommand(
        Long projectId,
        Long expectedVersion,
        String reasonCode,
        String reasonDetail,
        Long exceptionCloseSnapshotId,
        String idempotencyKey,
        String requestDigest,
        Long targetTemplateRevisionId) {
    public ReopenProjectCommand(Long projectId, Long expectedVersion, String reasonCode, String reasonDetail,
            Long exceptionCloseSnapshotId, String idempotencyKey, String requestDigest) {
        this(projectId, expectedVersion, reasonCode, reasonDetail, exceptionCloseSnapshotId, idempotencyKey, requestDigest, null);
    }
}
