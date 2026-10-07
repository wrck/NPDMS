package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 底层应用模板，保留既有 Owner 服务的扩展契约。普通实体使用 DefaultBusinessApplicationService；
 * 新增薄业务扩展使用 ExtensibleBusinessApplicationService，其公共安全步骤固定，子类只实现业务差异。
 *
 * 固定执行序：
 * 可信调用上下文 → 身份和入参 → 权限及状态 → 幂等与对象锁 → 本域命令 → 回执/审计/事件 → 提交。
 * 全序在调用方业务事务内执行；失败抛出异常回滚，不留事件、审计或幂等成功记录。
 */
public abstract class AbstractBusinessApplicationService<E extends BaseBusinessEntity> {

    private final BusinessEventPort eventPort;
    private final OperationAuditApi auditApi;
    private final TransactionOperations transactionOperations;

    protected AbstractBusinessApplicationService(BusinessEventPort eventPort, OperationAuditApi auditApi) {
        this(eventPort, auditApi, null);
    }

    protected AbstractBusinessApplicationService(BusinessEventPort eventPort, OperationAuditApi auditApi,
                                                 TransactionOperations transactionOperations) {
        this.eventPort = eventPort;
        this.auditApi = auditApi;
        this.transactionOperations = transactionOperations;
    }

    /** Reuse the configured transaction and audit/event ports in a thin default-service extension. */
    protected AbstractBusinessApplicationService(AbstractBusinessApplicationService<?> defaults) {
        this(defaults.eventPort, defaults.auditApi, defaults.transactionOperations);
    }

    /**
     * 执行一次领域意图。同键同意图重放返回原回执（不再执行领域命令，但访问权限已在本序内复查）；
     * 异载荷拒绝；失败整体回滚，网络未知结果通过查询回执恢复。
     */
    public final BusinessOperationReceipt execute(BusinessOperationRequest request) {
        return inBusinessTransaction(() -> doExecute(request));
    }

    /** Shared boundary for execution and receipt recovery, including inherited HTTP endpoints. */
    protected final <T> T inBusinessTransaction(java.util.function.Supplier<T> action) {
        return transactionOperations == null ? action.get() : transactionOperations.execute(status -> action.get());
    }

    private BusinessOperationReceipt doExecute(BusinessOperationRequest request) {
        ResolvedCaller caller = resolveCaller(request);
        validateIdentityAndInput(caller, request);
        authorizeAndCheckState(caller, request);
        LockedAggregate<E> locked;
        try {
            locked = lockAggregate(caller, request);
        } catch (ReplayedOperation replay) {
            // Replay is a successful reservation outcome. Resolve it before the transaction
            // callback returns, so joining a caller transaction does not mark it rollback-only.
            return replay.receipt();
        }
        BusinessOperationReceipt receipt = domainCommand(caller, request, locked);
        recordOutcome(caller, request, receipt);
        return receipt;
    }

    /** 可信调用上下文：服务端确定租户与操作者，不接受请求自报身份。 */
    protected abstract ResolvedCaller resolveCaller(BusinessOperationRequest request);

    /** 身份和入参校验：目标存在性、输入字段白名单、创建时实体 ID 尚未产生。 */
    protected abstract void validateIdentityAndInput(ResolvedCaller caller, BusinessOperationRequest request);

    /** 权限及状态：授权、数据范围与当前业务状态只允许合法动作。 */
    protected abstract void authorizeAndCheckState(ResolvedCaller caller, BusinessOperationRequest request);

    /** 幂等与对象锁：同键同意图返回原结果（抛 {@link ReplayedOperation}），异载荷拒绝。 */
    protected abstract LockedAggregate<E> lockAggregate(ResolvedCaller caller, BusinessOperationRequest request);

    /** 本域命令：默认行为（创建/保存）或专业扩展，由具体服务实现。 */
    protected abstract BusinessOperationReceipt domainCommand(
            ResolvedCaller caller, BusinessOperationRequest request, LockedAggregate<E> locked);

    /** 回执/审计/事件：真实业务变更与事件登记同事务；后台身份沿用已授权系统命令契约。 */
    protected abstract void recordOutcome(
            ResolvedCaller caller, BusinessOperationRequest request, BusinessOperationReceipt receipt);

    protected final BusinessEventPort events() {
        return eventPort;
    }

    protected final OperationAuditApi audit() {
        return auditApi;
    }

    /** 已解析的可信调用者（租户、用户、入口上下文）。 */
    public record ResolvedCaller(Long tenantId, Long userId, String entryCorrelationId) {
    }

    /** 已锁定的聚合及并发核对依据。 */
    public record LockedAggregate<E extends BaseBusinessEntity>(E aggregate, Long concurrencyBasis,
                                                               java.util.Map<String, Object> changes) {
        public LockedAggregate(E aggregate, Long concurrencyBasis) { this(aggregate, concurrencyBasis, null); }
    }

    /** 幂等重放：携带可重复查询的原始回执，由模板方法直接返回，不再执行领域命令。 */
    public static final class ReplayedOperation extends RuntimeException {

        private final BusinessOperationReceipt receipt;

        public ReplayedOperation(BusinessOperationReceipt receipt) {
            super("幂等键重放：返回原始回执");
            this.receipt = receipt;
        }

        public BusinessOperationReceipt receipt() {
            return receipt;
        }
    }
}
