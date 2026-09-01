package cn.iocoder.yudao.module.pms.project.service.stagegate.command;

public record ProjectStageAdvanceCommand(
        Long projectId, Integer expectedProjectVersion, String expectedCurrentStage,
        Long expectedTreeVersion, String idempotencyKey, String requestDigest) {
}
