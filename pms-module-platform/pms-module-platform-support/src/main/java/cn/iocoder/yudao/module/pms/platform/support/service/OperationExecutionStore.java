package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;

import java.util.Optional;
import java.util.function.Function;

/**
 * 统一幂等执行记录存储：与既有平台幂等台账同语义（预约 IN_PROGRESS、完成 COMPLETED、
 * 请求摘要一致性判定），由运行时按自身表实现；框架默认执行序只依赖本端口。
 */
public interface OperationExecutionStore {

    /**
     * 预约执行：同键首次返回 true；已存在返回 false（由调用方按摘要判定重放或冲突）。
     */
    boolean reserve(OperationExecutionKey key, String requestDigest);

    /**
     * 查询既有执行记录；无记录返回 empty。重放仍须先通过访问权限检查（由固定执行序保证）。
     */
    Optional<StoredExecution> findExisting(OperationExecutionKey key);

    /** Decode an existing native response at the Owner boundary without creating another ledger. */
    default Optional<StoredExecution> findExisting(OperationExecutionKey key,
                                                  Function<String, BusinessOperationReceipt> decoder) {
        return findExisting(key);
    }

    /** Preserve a native response protocol while exposing a neutral operation receipt. */
    default void complete(OperationExecutionKey key, String aggregateType, String resourceKey,
                          BusinessOperationReceipt receipt, String nativeResponsePayload) {
        complete(key, aggregateType, resourceKey, receipt);
    }

    /**
     * 完成执行：登记聚合身份与原始回执，回执可重复查询；必须在业务事务内调用。
     */
    void complete(OperationExecutionKey key, String aggregateType, String resourceKey,
                  BusinessOperationReceipt receipt);

    /** 幂等键命名空间：租户 + 场景（业务域/实体） + 操作者 + 幂等键。 */
    record OperationExecutionKey(Long tenantId, String scopeCode, Long actorId, String idempotencyKey) {
    }

    /** 既有执行记录：请求摘要、状态与已完成的原始回执。 */
    record StoredExecution(String requestDigest, String status, BusinessOperationReceipt receipt) {
    }
}
