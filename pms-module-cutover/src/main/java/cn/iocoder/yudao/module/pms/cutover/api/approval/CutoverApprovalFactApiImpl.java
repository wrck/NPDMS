package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalOwnerFactException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

import java.util.Objects;

import static cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactException.Code.*;

/** CUT审批公开事实Provider候选；不在Task 13前注册生产Bean。 */
public class CutoverApprovalFactApiImpl implements CutoverApprovalFactApi {
    private final CutoverApprovalFactTransactionExecutor transactions;

    public CutoverApprovalFactApiImpl(CutoverApprovalFactTransactionExecutor transactions) {
        this.transactions = transactions;
    }

    @Override
    public CutoverApprovalStartResult start(CutoverApprovalStartCommand command) {
        requireTenant(command.tenantId());
        try { return transactions.start(command); }
        catch (RuntimeException ex) { throw translateBoundary(ex); }
    }

    @Override
    public CutoverApprovalInspectResult inspect(CutoverApprovalFactQuery query) {
        requireTenant(query.tenantId());
        try { return transactions.inspect(query); }
        catch (RuntimeException ex) { throw translateBoundary(ex); }
    }

    @Override
    public CutoverApprovalRevalidationResult lockAndRevalidate(CutoverApprovalRevalidationQuery query) {
        requireTenant(query.tenantId());
        try { return transactions.lockAndRevalidate(query); }
        catch (RuntimeException ex) { throw translateBoundary(ex); }
    }

    @Override
    public CutoverApprovalCommandResult pauseForSourceInvalidation(CutoverApprovalPauseCommand command) {
        requireTenant(command.tenantId());
        try { return transactions.pauseForSourceInvalidation(command); }
        catch (RuntimeException ex) { throw translateBoundary(ex); }
    }

    private static void requireTenant(Long tenantId) {
        Long current = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(current, tenantId))
            throw new CutoverApprovalFactException(TENANT_CONTEXT_MISMATCH, "受信租户与请求租户不一致");
    }

    private static CutoverApprovalFactException translateBoundary(RuntimeException exception) {
        if (exception instanceof CutoverApprovalFactException fact) return fact;
        if (exception instanceof DataAccessException || exception instanceof TransactionException) {
            return new CutoverApprovalFactException(PROVIDER_UNAVAILABLE, "审批事实Provider不可用", exception);
        }
        if (exception instanceof CutoverApprovalOwnerFactException owner) {
            return switch (owner.code()) {
                case PROVIDER_UNAVAILABLE -> new CutoverApprovalFactException(PROVIDER_UNAVAILABLE, owner.getMessage(), owner);
                case OWNER_DATA_CORRUPTED -> new CutoverApprovalFactException(OWNER_DATA_CORRUPTED, owner.getMessage(), owner);
                default -> new CutoverApprovalFactException(OWNER_DATA_CORRUPTED, owner.getMessage(), owner);
            };
        }
        if (!(exception instanceof CutoverApprovalApplicationException application)) throw exception;
        CutoverApprovalFactException.Code code = switch (application.code()) {
            case INVALID_REQUEST -> INVALID_REQUEST;
            case STATE_CONFLICT -> STATE_CONFLICT;
            case VERSION_CONFLICT, SOURCE_STALE -> VERSION_CONFLICT;
            case IDEMPOTENCY_CONFLICT -> IDEMPOTENCY_CONFLICT;
            case IDEMPOTENCY_IN_PROGRESS -> IDEMPOTENCY_IN_PROGRESS;
            case BUSINESS_INCOMPLETE, OWNER_DATA_CORRUPTED -> OWNER_DATA_CORRUPTED;
            case OWNER_PROVIDER_UNAVAILABLE -> PROVIDER_UNAVAILABLE;
        };
        return new CutoverApprovalFactException(code, application.getMessage(), application);
    }
}
