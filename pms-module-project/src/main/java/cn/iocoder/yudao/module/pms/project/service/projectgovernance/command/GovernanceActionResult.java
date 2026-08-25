package cn.iocoder.yudao.module.pms.project.service.projectgovernance.command;

import java.time.LocalDateTime;

public record GovernanceActionResult(
        Long projectId,
        String action,
        String beforeLifecycleStatus,
        String beforeStage,
        String beforeAssignmentStatus,
        String lifecycleStatus,
        String currentStage,
        String assignmentStatus,
        Integer projectVersion,
        Long stageSnapshotId,
        String operationId,
        LocalDateTime operatedAt,
        boolean replayed) {
}
