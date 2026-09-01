package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalOwnerFactException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactException.Code.*;

/** CUT审批公开事实Provider候选；不在Task 13前注册生产Bean。 */
public class CutoverApprovalFactApiImpl implements CutoverApprovalFactApi {
    private final CutoverApprovalApplicationService applicationService;
    private final CutoverApprovalInstanceMapper instanceMapper;

    public CutoverApprovalFactApiImpl(CutoverApprovalApplicationService applicationService,
                                      CutoverApprovalInstanceMapper instanceMapper) {
        this.applicationService = applicationService;
        this.instanceMapper = instanceMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CutoverApprovalStartResult start(CutoverApprovalStartCommand command) {
        requireTenant(command.tenantId());
        try { return applicationService.start(command); }
        catch (CutoverApprovalApplicationException | CutoverApprovalOwnerFactException ex) { throw translate(ex); }
    }

    @Override
    @Transactional(readOnly = true)
    public CutoverApprovalInspectResult inspect(CutoverApprovalFactQuery query) {
        requireTenant(query.tenantId());
        CutoverApprovalInstanceDO row = instanceMapper.selectOne(new LambdaQueryWrapperX<CutoverApprovalInstanceDO>()
                .eq(CutoverApprovalInstanceDO::getTenantId, query.tenantId())
                .eq(CutoverApprovalInstanceDO::getTaskId, query.taskId())
                .eq(CutoverApprovalInstanceDO::getPlanRevisionId, query.planRevisionId()));
        return row == null ? new CutoverApprovalInspectResult(InspectStatus.NOT_FOUND, null)
                : new CutoverApprovalInspectResult(InspectStatus.FOUND, CutoverApprovalApplicationService.fact(row));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CutoverApprovalRevalidationResult lockAndRevalidate(CutoverApprovalRevalidationQuery query) {
        requireTenant(query.tenantId());
        ExpectedCutoverApprovalFact expected = query.expected();
        CutoverApprovalInstanceDO row = instanceMapper.selectByIdForUpdate(new ApprovalInstanceLockQuery(
                query.tenantId(), expected.approvalInstanceId(), null, null));
        if (row == null || !Objects.equals(row.getTaskId(), expected.taskId())
                || !Objects.equals(row.getPlanRevisionId(), expected.planRevisionId())) {
            throw new CutoverApprovalFactException(NOT_FOUND, "审批事实不存在");
        }
        CutoverApprovalFact current = CutoverApprovalApplicationService.fact(row);
        boolean same = Objects.equals(current.approvalInstanceId(), expected.approvalInstanceId())
                && Objects.equals(current.approvalVersion(), expected.approvalVersion())
                && Objects.equals(current.taskId(), expected.taskId())
                && Objects.equals(current.planRevisionId(), expected.planRevisionId())
                && Objects.equals(current.planRevisionNo(), expected.planRevisionNo())
                && current.status() == expected.status()
                && Objects.equals(current.sourceSnapshotVersion(), expected.sourceSnapshotVersion())
                && Objects.equals(current.replacementApprovalInstanceId(), expected.replacementApprovalInstanceId())
                && Objects.equals(current.decisionAt(), expected.decisionAt())
                && Objects.equals(current.rejectionReason(), expected.rejectionReason());
        return new CutoverApprovalRevalidationResult(same ? RevalidationStatus.VALID : RevalidationStatus.STALE,
                current);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CutoverApprovalCommandResult pauseForSourceInvalidation(CutoverApprovalPauseCommand command) {
        requireTenant(command.tenantId());
        try { return applicationService.pause(command); }
        catch (CutoverApprovalApplicationException | CutoverApprovalOwnerFactException ex) { throw translate(ex); }
    }

    private static void requireTenant(Long tenantId) {
        Long current = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(current, tenantId))
            throw new CutoverApprovalFactException(TENANT_CONTEXT_MISMATCH, "受信租户与请求租户不一致");
    }

    private static CutoverApprovalFactException translate(RuntimeException exception) {
        if (exception instanceof CutoverApprovalOwnerFactException owner) {
            return switch (owner.code()) {
                case PROVIDER_UNAVAILABLE -> new CutoverApprovalFactException(PROVIDER_UNAVAILABLE, owner.getMessage(), owner);
                case OWNER_DATA_CORRUPTED -> new CutoverApprovalFactException(OWNER_DATA_CORRUPTED, owner.getMessage(), owner);
                default -> new CutoverApprovalFactException(OWNER_DATA_CORRUPTED, owner.getMessage(), owner);
            };
        }
        CutoverApprovalApplicationException application = (CutoverApprovalApplicationException) exception;
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
