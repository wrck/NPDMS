package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactException.Code.NOT_FOUND;

/** 承接CUT审批公开事实的完整Spring事务阶段；生产注册留待依赖接通Gate。 */
public class CutoverApprovalFactTransactionExecutor {
    private final CutoverApprovalApplicationService applicationService;
    private final CutoverApprovalInstanceMapper instanceMapper;

    public CutoverApprovalFactTransactionExecutor(CutoverApprovalApplicationService applicationService,
                                                   CutoverApprovalInstanceMapper instanceMapper) {
        this.applicationService = applicationService;
        this.instanceMapper = instanceMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CutoverApprovalStartResult start(CutoverApprovalStartCommand command) {
        return applicationService.start(command);
    }

    @Transactional(readOnly = true)
    public CutoverApprovalInspectResult inspect(CutoverApprovalFactQuery query) {
        CutoverApprovalInstanceDO row = instanceMapper.selectOne(new LambdaQueryWrapperX<CutoverApprovalInstanceDO>()
                .eq(CutoverApprovalInstanceDO::getTenantId, query.tenantId())
                .eq(CutoverApprovalInstanceDO::getTaskId, query.taskId())
                .eq(CutoverApprovalInstanceDO::getPlanRevisionId, query.planRevisionId()));
        return row == null ? new CutoverApprovalInspectResult(InspectStatus.NOT_FOUND, null)
                : new CutoverApprovalInspectResult(InspectStatus.FOUND, CutoverApprovalApplicationService.fact(row));
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CutoverApprovalRevalidationResult lockAndRevalidate(CutoverApprovalRevalidationQuery query) {
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

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CutoverApprovalCommandResult pauseForSourceInvalidation(CutoverApprovalPauseCommand command) {
        return applicationService.pause(command);
    }
}
