package cn.iocoder.yudao.module.pms.project.service.stagegate.command;

import java.time.LocalDateTime;

public record ProjectStageAdvanceResult(
        Long projectId, String beforeStage, String afterStage, Integer projectVersion,
        Long stageSnapshotId, String gateEvaluationSummary, String operationId,
        LocalDateTime operatedAt, boolean replayed) {
}
