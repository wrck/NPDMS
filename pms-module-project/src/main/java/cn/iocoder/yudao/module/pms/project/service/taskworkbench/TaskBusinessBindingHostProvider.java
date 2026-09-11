package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.CurrentTaskAssignmentsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskByIdQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachineRevisionLockQuery;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** PM-11: task actions only; Owner query/save grants never become task completion grants. */
@Component
@RequiredArgsConstructor
public class TaskBusinessBindingHostProvider implements TaskBindingHostProvider {
    public static final Set<String> TYPES = Set.of("BUSINESS_OBJECT", "BUSINESS_COMPONENT");
    private static final Map<String, String> PERMISSIONS = Map.of(
            "START", "pms:project-task:execute", "SUBMIT", "pms:project-task:execute",
            "COMPLETE", "pms:project-task:complete", "CANCEL", "pms:project-task:complete");
    private final ProjectTaskBusinessService businessService;
    private final ProjectTaskRuntimeMapper taskMapper;
    private final ProjectTaskAssignmentMapper assignmentMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final TaskStateMachineMapper stateMachineMapper;
    private final ProjectScopeApi scopeApi;
    private final PermissionApi permissionApi;

    @Override public String bindingType() { return "BUSINESS_OBJECT"; }
    @Override public Set<String> bindingTypes() { return TYPES; }

    @Override
    public TaskBindingInspection inspect(TaskBindingInspectionQuery query) {
        if (query == null || query.tenantId() == null || query.taskId() == null || query.actorId() == null) {
            return TaskBindingInspection.failed(bindingType(), "BINDING_FACT_UNKNOWN");
        }
        var context = businessService.getContext(query.taskId(), query.tenantId(), query.actorId(), query.correlationId());
        if (context == null || context.recoverableError() != null || context.factVersion() == null) {
            return TaskBindingInspection.failed(bindingType(), context == null || context.recoverableError() == null
                    ? "BINDING_FACT_UNKNOWN" : context.recoverableError());
        }
        var task = taskMapper.selectTask(new TaskByIdQuery(query.tenantId(), query.taskId()));
        if (task == null || !Objects.equals(task.getProjectId(), context.projectId())) {
            return TaskBindingInspection.failed(bindingType(), "BINDING_FACT_UNKNOWN");
        }
        boolean superAdmin = Objects.equals(query.tenantId(), cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId())
                && permissionApi.hasAnyRoles(query.actorId(), cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum.SUPER_ADMIN.getCode());
        boolean assignee = superAdmin || assignmentMapper.selectCurrent(new CurrentTaskAssignmentsQuery(query.tenantId(),
                Set.of(task.getId()))).stream().anyMatch(row -> Objects.equals(row.getAssigneeUserId(), query.actorId()));
        boolean manager = superAdmin || memberMapper.selectActiveByUser(new ActiveProjectMemberQuery(query.tenantId(),
                query.actorId(), LocalDateTime.now())).stream().anyMatch(row ->
                Objects.equals(task.getProjectId(), row.getProjectId()) && "PROJECT_MANAGER".equals(row.getMemberRole()));
        Set<String> allowed = new HashSet<>();
        for (var transition : stateMachineMapper.selectTransitions(new TaskStateMachineRevisionLockQuery(
                query.tenantId(), task.getStateMachineRevisionId()))) {
            String action = transition.getActionCode();
            if (!Objects.equals(task.getStatus(), transition.getFromStatusCode()) || !PERMISSIONS.containsKey(action)) continue;
            boolean execute = "START".equals(action) || "SUBMIT".equals(action);
            boolean roleAllowed = switch (String.valueOf(transition.getAllowedRoleCode())) {
                case "CURRENT_EFFECTIVE_ASSIGNEE" -> assignee;
                case "CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER",
                     "CURRENT_PROJECT_MANAGER_OR_AUTHORIZED_SERVICE_MANAGER_FOR_CROSS_REGION" -> manager;
                default -> false;
            };
            if (!roleAllowed || (execute ? !assignee : !manager)
                    || !permissionApi.hasAnyPermissions(query.actorId(), PERMISSIONS.get(action))) continue;
            var scope = scopeApi.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(), query.actorId(),
                    task.getProjectId(), execute ? ProjectScopeApi.ACTION_EDIT : ProjectScopeApi.ACTION_MANAGE));
            if (scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(task.getProjectId())) {
                allowed.add(action);
            }
        }
        return new TaskBindingInspection(bindingType(), Set.copyOf(allowed), context.factVersion(), null);
    }
}
