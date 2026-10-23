package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
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

    private boolean restricted() {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        return actor != null && (permissions.hasAnyRoles(actor, "SERVICE_MANAGER")
                || permissions.hasAnyRoles(actor, "PROJECT_MANAGER"))
                && !permissions.hasAnyPermissions(actor, "bpm:task:manager-query");
    }

    public void requireProcess(String processInstanceId) {
        if (!restricted()) return;
        String actor = String.valueOf(SecurityFrameworkUtils.getLoginUserId());
        String tenant = String.valueOf(TenantContextHolder.getRequiredTenantId());
        var process = history.createHistoricProcessInstanceQuery().includeProcessVariables().processInstanceId(processInstanceId)
                .processInstanceTenantId(tenant).singleResult();
        if (process == null) throw exception(FORBIDDEN);
        if (permissions.hasAnyRoles(SecurityFrameworkUtils.getLoginUserId(), "PROJECT_MANAGER")
                && process.getBusinessKey() != null && process.getBusinessKey().startsWith("SOL_REVIEW:" + tenant + ":")) {
            var variables = process.getProcessVariables();
            Object project = variables == null ? null : variables.get("projectId");
            if (project == null) throw exception(FORBIDDEN);
            Long projectId;
            try { projectId = Long.valueOf(project.toString()); }
            catch (NumberFormatException invalid) { throw exception(FORBIDDEN); }
            var visible = projectScope.resolveCurrent(cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery.builder()
                    .tenantId(TenantContextHolder.getRequiredTenantId()).subjectUserId(SecurityFrameworkUtils.getLoginUserId())
                    .anchorProjectId(projectId).actionCode(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW).build());
            if (visible == null || !visible.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
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
        if (!restricted()) return;
        var task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw exception(FORBIDDEN);
        requireProcess(task.getProcessInstanceId());
    }

    public void requireAssignedTask(String taskId) {
        if (!restricted()) return;
        var task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || !Objects.equals(task.getTenantId(), String.valueOf(TenantContextHolder.getRequiredTenantId()))
                || !Objects.equals(task.getAssignee(), String.valueOf(SecurityFrameworkUtils.getLoginUserId()))) throw exception(FORBIDDEN);
    }

    /** The approval grant does not include reassignment, sign manipulation, copying or generic process creation. */
    public void requireAdvancedAction() {
        if (restricted()) throw exception(FORBIDDEN);
    }
}
