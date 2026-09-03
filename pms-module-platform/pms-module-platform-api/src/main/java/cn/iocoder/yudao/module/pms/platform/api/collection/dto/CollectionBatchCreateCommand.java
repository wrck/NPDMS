package cn.iocoder.yudao.module.pms.platform.api.collection.dto;

import java.util.List;

public record CollectionBatchCreateCommand(
        Long tenantId,
        Long actorId,
        String idempotencyKey,
        String requestDigest,
        String sourceContext,
        String sourceObjectType,
        String sourceObjectId,
        String projectId,
        String completionMode,
        List<CollectionTaskCreateItem> tasks) {
}
