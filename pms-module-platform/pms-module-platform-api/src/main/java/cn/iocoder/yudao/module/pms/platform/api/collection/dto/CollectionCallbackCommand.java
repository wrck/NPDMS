package cn.iocoder.yudao.module.pms.platform.api.collection.dto;

import java.time.LocalDateTime;

public record CollectionCallbackCommand(
        Long receiptId,
        String callbackId,
        Long sequence,
        String platformTaskId,
        String externalTaskId,
        String externalStatus,
        Long resultVersion,
        Long fileVersionId,
        String quarantineEvidenceId,
        String failureCategory,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String traceId) {
}
