package cn.iocoder.yudao.module.pms.project.api.commerce.dto;

public record ProjectAcceptanceStageFact(
        ProjectFactOutcome outcome,
        Long projectId,
        Long projectVersion,
        String currentStageCode,
        String acceptanceStageCode,
        Long projectStageSnapshotId) {
}
