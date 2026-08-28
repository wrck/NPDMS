package cn.iocoder.yudao.module.pms.integration.api.deviceops.dto;

import java.time.OffsetDateTime;

public record DeviceOpsTaskSnapshot(
        String platformTaskId,
        String externalTaskId,
        String externalStatus,
        String failureCategory,
        Long resultVersion,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String traceId) {
}
