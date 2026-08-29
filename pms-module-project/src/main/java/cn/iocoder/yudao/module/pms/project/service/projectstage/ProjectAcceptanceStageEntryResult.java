package cn.iocoder.yudao.module.pms.project.service.projectstage;

import java.time.LocalDateTime;

public record ProjectAcceptanceStageEntryResult(
        Long projectId,
        String beforeStageCode,
        String acceptanceStageCode,
        Integer projectVersion,
        Long projectStageSnapshotId,
        int bindingCount,
        String operationId,
        LocalDateTime operatedAt,
        boolean replayed) {
}
