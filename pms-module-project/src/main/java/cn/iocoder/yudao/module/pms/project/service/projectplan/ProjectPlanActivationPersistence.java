package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PLAN_VERSION_CONFLICT;

/** Final write phase of an authorized plan command, after runtime projections/contracts have been installed. */
@Component
@RequiredArgsConstructor
public class ProjectPlanActivationPersistence {
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;

    /** Caller owns authorization, compilation, the project lock, projection writes and same-transaction audit/Outbox. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void activate(ProjectPlanVersionMapper.Activation plan,
                         List<ProjectNodeExecutionMapper.PlanRebase> continuing,
                         List<ProjectNodeExecutionMapper.PlanRetirement> removed) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("PLAN_ACTIVATION_TRANSACTION_REQUIRED");
        if (!Objects.equals(TenantContextHolder.getRequiredTenantId(), plan.tenantId())
                || Objects.equals(plan.oldPlanVersionId(), plan.draftId()))
            throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        // Validate the whole batch before writing; a stale/cross-project token never moves another execution.
        for (var row : continuing) {
            if (!Objects.equals(plan.tenantId(), row.tenantId()) || !Objects.equals(plan.projectId(), row.projectId())
                    || !Objects.equals(plan.oldPlanVersionId(), row.oldPlanVersionId())
                    || !Objects.equals(plan.draftId(), row.newPlanVersionId())) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        }
        for (var row : removed) {
            if (!Objects.equals(plan.tenantId(), row.tenantId()) || !Objects.equals(plan.projectId(), row.projectId())
                    || !Objects.equals(plan.oldPlanVersionId(), row.planVersionId())) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        }
        for (var row : continuing) requireOne(executions.rebaseUnfinishedIfCurrent(row));
        for (var row : removed) requireOne(executions.retireUnstartedIfCurrent(row));
        requireOne(plans.supersedeEffectiveIfCurrent(plan));
        requireOne(plans.promoteDraftIfCurrent(plan));
        requireOne(plans.attachActivatedPlan(plan));
    }

    private void requireOne(int affected) { if (affected != 1) throw exception(PROJECT_PLAN_VERSION_CONFLICT); }
}
