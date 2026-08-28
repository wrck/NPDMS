package cn.iocoder.yudao.module.pms.asset.service.assignment.command;

import java.time.LocalDateTime;

public record DeviceAssignedPayload(
        Long deviceId,
        Long oldProjectId,
        Long newProjectId,
        Long assignmentVersion,
        LocalDateTime effectiveAt,
        String operationId) {
}
