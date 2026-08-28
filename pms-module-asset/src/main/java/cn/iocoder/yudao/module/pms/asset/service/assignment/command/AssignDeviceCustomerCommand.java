package cn.iocoder.yudao.module.pms.asset.service.assignment.command;

import java.time.LocalDateTime;

public record AssignDeviceCustomerCommand(
        Long tenantId,
        Long deviceId,
        Long customerId,
        String relationshipType,
        Long expectedAssignmentVersion,
        String reason,
        String idempotencyKey,
        String requestDigest,
        Long actorId,
        String correlationId,
        LocalDateTime effectiveAt) {
}
