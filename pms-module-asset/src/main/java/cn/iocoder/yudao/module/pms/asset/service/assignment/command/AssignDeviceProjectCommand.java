package cn.iocoder.yudao.module.pms.asset.service.assignment.command;

import java.time.LocalDateTime;

public record AssignDeviceProjectCommand(
        Long tenantId,
        Long deviceId,
        Long projectId,
        Long expectedAssignmentVersion,
        String reason,
        String idempotencyKey,
        String requestDigest,
        Long actorId,
        String correlationId,
        LocalDateTime effectiveAt) {
}
