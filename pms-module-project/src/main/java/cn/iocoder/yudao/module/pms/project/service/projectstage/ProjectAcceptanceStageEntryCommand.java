package cn.iocoder.yudao.module.pms.project.service.projectstage;

public record ProjectAcceptanceStageEntryCommand(
        Long projectId,
        Integer expectedProjectVersion,
        Long expectedTreeVersion,
        String idempotencyKey,
        String requestDigest) {
}
