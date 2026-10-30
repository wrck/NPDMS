package cn.iocoder.yudao.module.pms.project.service.projectgovernance.command;

public record RollbackProjectCommand(
        Long projectId,
        Long expectedVersion,
        String guardToken,
        String reasonCode,
        String reasonDetail,
        String reassignmentRequirement,
        String idempotencyKey,
        String requestDigest) {
}
