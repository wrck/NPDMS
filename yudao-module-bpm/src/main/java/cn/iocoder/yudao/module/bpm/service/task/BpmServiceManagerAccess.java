package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Constrains service-manager approval grants to participated processes and assigned tasks. */
@Service
@RequiredArgsConstructor
public class BpmServiceManagerAccess {
    private final PermissionApi permissions;
    private final HistoryService history;
    private final TaskService tasks;
    private final cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi projectScope;
    private final cn.iocoder.yudao.module.bpm.service.solutionreview.SolutionReviewRoleTasks solutionRoles;

    private boolean restricted() {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        return actor != null && (permissions.hasAnyRoles(actor, ProjectMemberRoles.MANAGEMENT_CODES.toArray(String[]::new))
                || permissions.hasAnyRoles(actor, cn.iocoder.yudao.module.bpm.api.solutionreview.SolutionReviewBpmApi.ENGINEERING_REVIEW_ROLE))
                && !permissions.hasAnyPermissions(actor, "bpm:task:manager-query");
    }

    public void requireProcess(String processInstanceId) {
        String actor = String.valueOf(SecurityFrameworkUtils.getLoginUserId());
        String tenant = String.valueOf(TenantContextHolder.getRequiredTenantId());
        var process = history.createHistoricProcessInstanceQuery().includeProcessVariables().processInstanceId(processInstanceId)
                .processInstanceTenantId(tenant).singleResult();
        if (process == null) throw exception(FORBIDDEN);
        boolean solution = process.getBusinessKey() != null && process.getBusinessKey().startsWith("SOL_REVIEW:" + tenant + ":");
        boolean sharedSolution = solution && history.getHistoricIdentityLinksForProcessInstance(processInstanceId).stream()
                .anyMatch(link -> link.getType() != null && link.getType().startsWith("solutionReview:role:"));
        if (sharedSolution) {
            var access = solutionRoles.read(new cn.iocoder.yudao.module.bpm.api.solutionreview.SolutionReviewBpmApi.ReadQuery(
                    TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), process.getBusinessKey()));
            if (access != null && Objects.equals(access.processInstanceId(), processInstanceId)
                    && Objects.equals(access.businessKey(), process.getBusinessKey()) || ordinaryProjectView(process)) return;
            throw exception(FORBIDDEN);
        }
        if (!restricted()) return;
        if (permissions.hasAnyRoles(SecurityFrameworkUtils.getLoginUserId(), ProjectMemberRoles.MANAGEMENT_CODES.toArray(String[]::new))
                && process.getBusinessKey() != null && process.getBusinessKey().startsWith("SOL_REVIEW:" + tenant + ":")) {
            if (!ordinaryProjectView(process)) throw exception(FORBIDDEN);
            return;
        }
        if (actor.equals(process.getStartUserId())) return;
        if (history.createHistoricTaskInstanceQuery().processInstanceId(processInstanceId)
                .taskTenantId(tenant).taskAssignee(actor).count() > 0) return;
        if (history.createHistoricTaskInstanceQuery().processInstanceId(processInstanceId)
                .taskTenantId(tenant).taskOwner(actor).count() > 0) return;
        throw exception(FORBIDDEN);
    }

    public void requireTask(String taskId) {
        var task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw exception(FORBIDDEN);
        requireProcess(task.getProcessInstanceId());
    }

    private boolean ordinaryProjectView(org.flowable.engine.history.HistoricProcessInstance process) {
        var variables = process.getProcessVariables();
        Object project = variables == null ? null : variables.get("projectId");
        if (project == null) return false;
        Long projectId;
        try { projectId = Long.valueOf(project.toString()); }
        catch (NumberFormatException invalid) { return false; }
        var visible = projectScope.resolveCurrent(cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).subjectUserId(SecurityFrameworkUtils.getLoginUserId())
                .anchorProjectId(projectId).actionCode(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW).build());
        return visible != null && visible.fullProjectIds().contains(projectId);
    }

    public void requireAssignedTask(String taskId) {
        if (!restricted()) return;
        var task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || !Objects.equals(task.getTenantId(), String.valueOf(TenantContextHolder.getRequiredTenantId()))
                || !Objects.equals(task.getAssignee(), String.valueOf(SecurityFrameworkUtils.getLoginUserId()))
                && !solutionRoles.canReadTask(new cn.iocoder.yudao.module.bpm.service.solutionreview.SolutionReviewRoleTasks.DecisionActor(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), taskId))) throw exception(FORBIDDEN);
    }


    /** The approval grant does not include reassignment, sign manipulation, copying or generic process creation. */
    public void requireAdvancedAction() {
        if (restricted()) throw exception(FORBIDDEN);
    }
}
