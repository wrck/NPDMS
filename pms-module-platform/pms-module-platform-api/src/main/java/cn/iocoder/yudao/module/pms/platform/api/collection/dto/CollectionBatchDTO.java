package cn.iocoder.yudao.module.pms.platform.api.collection.dto;

import java.util.List;

public record CollectionBatchDTO(
        Long id,
        String batchNo,
        String sourceContext,
        String sourceObjectType,
        String sourceObjectId,
        String idempotencyKey,
        String status,
        Integer taskCount,
        List<CollectionTaskDTO> tasks) {
}
