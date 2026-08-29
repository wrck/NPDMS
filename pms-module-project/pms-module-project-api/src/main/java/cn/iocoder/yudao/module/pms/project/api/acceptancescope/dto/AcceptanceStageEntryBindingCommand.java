package cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto;

public record AcceptanceStageEntryBindingCommand(
        Long tenantId,
        Long projectId,
        Integer projectVersion,
        Long projectStageSnapshotId,
        String fromStageCode,
        String acceptanceStageCode,
        String operationId) {
}
