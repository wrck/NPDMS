package cn.iocoder.yudao.module.pms.asset.service.assignment.command;

import java.util.List;

public record RebuildDeviceAncestorProjectionCommand(
        Long tenantId,
        String deviceSn,
        Long projectId,
        List<Long> ancestorProjectIds,
        Long treeVersion,
        Long assignmentVersion,
        String eventId,
        String operationId) {
}
