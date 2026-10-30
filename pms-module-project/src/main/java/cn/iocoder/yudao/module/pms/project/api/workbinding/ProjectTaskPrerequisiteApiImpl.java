package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reads the current frozen plan, without changing task/round state or historical contracts. */
@Service
@RequiredArgsConstructor
public class ProjectTaskPrerequisiteApiImpl implements ProjectTaskPrerequisiteApi {
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectTaskExecutionContractMapper contracts;
    // Admission resolves Owner fact providers, including SOL; obtain it only when checking a request.
    private final ObjectProvider<ProjectStageAdmissionService> admission;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Readiness inspect(Query query) {
        return evaluate(query);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void requireSatisfied(Query query) {
        var result = evaluate(query);
        if (!result.allowed()) throw new IllegalArgumentException("暂不能提交审核：" + String.join("、", result.blockers()));
    }

    private Readiness evaluate(Query query) {
        if (query == null || query.projectId() == null || query.ownerContext() == null || query.objectType() == null)
            return blocked("业务任务上下文缺失");
        Long tenant = TenantContextHolder.getRequiredTenantId();
        // Matches project command lock order. This also serializes plan replacement and task completion.
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenant, query.projectId()));
        if (project == null || !Objects.equals(project.getTenantId(), tenant)
                || !Objects.equals(project.getId(), query.projectId())) return blocked("项目不可用");
        if (!"ACTIVE".equals(project.getLifecycleStatus())) return blocked("项目当前不允许提交审核");
        if (project.getActivePlanVersionId() == null) {
            return project.getLifecycleTemplateId() == null && project.getLifecycleTemplateRevisionId() == null
                    ? ready() : blocked("项目计划尚未初始化");
        }
        var plan = plans.selectEffective(new ProjectPlanScopeQuery(tenant, query.projectId()));
        if (plan == null || !Objects.equals(plan.getId(), project.getActivePlanVersionId()))
            return blocked("当前项目计划不可用");
        try {
            var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
            var definitions = snapshot.getTasks().stream().filter(task -> task.getBinding() != null
                    && query.ownerContext().equals(task.getBinding().getTargetContextCode())
                    && query.objectType().equals(task.getBinding().getTargetObjectType())).toList();
            if (definitions.isEmpty()) return ready(); // Independent business, with no task binding in this plan.
            if (definitions.size() != 1) return blocked("当前计划存在多个对应业务任务，无法确定提交前置条件");
            var definition = definitions.getFirst();
            var tasks = graph.selectTasksForUpdate(new ProjectRuntimeGraphQuery(tenant, query.projectId())).stream()
                    .filter(task -> Objects.equals(task.getCode(), definition.getCode())
                            && Objects.equals(task.getStageCode(), definition.getStageCode())).toList();
            if (tasks.size() != 1) return blocked("当前计划业务任务不可用");
            var task = tasks.getFirst();
            if (Set.of("CLOSED", "CANCELLED").contains(task.getStatus())) return blocked("业务任务已关闭或取消");
            var contract = contracts.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(tenant, task.getId()));
            if (contract == null || !Objects.equals(contract.getTenantId(), tenant)
                    || !Objects.equals(contract.getProjectTaskId(), task.getId()) || contract.getEffectiveTo() != null
                    || !Objects.equals(contract.getSourceNodeKey(), definition.getNodeKey())
                    || !query.ownerContext().equals(contract.getTargetContextCode())
                    || !query.objectType().equals(contract.getTargetObjectType())) return blocked("当前任务执行契约不可用");
            var result = admission.getObject().submissionPrerequisites(new ProjectStageAdmissionService.TaskPrerequisiteQuery(project, task, contract));
            if (result.outcome() == RuleEvaluation.Outcome.MATCHED) return ready();
            if (result.outcome() == RuleEvaluation.Outcome.UNKNOWN) return blocked("任务前置条件暂无法判定，请刷新后重试");
            var labels = result.conditions().stream().filter(item -> item.outcome() != RuleEvaluation.Outcome.MATCHED)
                    .map(ProjectStageAdmissionService.TaskAdmissionCondition::label).distinct().toList();
            return new Readiness(false, labels.isEmpty() ? List.of("任务前置条件尚未满足") : labels);
        } catch (RuntimeException unavailable) {
            return blocked("任务前置条件暂无法判定，请刷新后重试");
        }
    }

    private static Readiness ready() { return new Readiness(true, List.of()); }
    private static Readiness blocked(String reason) { return new Readiness(false, List.of(reason)); }
}
