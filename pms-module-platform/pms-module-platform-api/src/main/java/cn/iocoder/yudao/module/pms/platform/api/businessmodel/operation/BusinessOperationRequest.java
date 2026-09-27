package cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;

import java.util.Map;

/**
 * 操作请求：操作编码及版本、实体目标或创建上下文、输入、幂等键、并发依据、入口上下文。
 * 服务端确定租户和操作者；创建时允许实体 ID 尚未产生（targetRef 为空，身份在 ownerModule/entityType 中）。
 */
public record BusinessOperationRequest(
        String operationCode,
        int operationVersion,
        EntityDataRef targetRef,
        String ownerModule,
        String entityType,
        Map<String, Object> input,
        String idempotencyKey,
        Long concurrencyBasis,
        OperationEntryKind entryKind,
        String entryCorrelationId) {
}
