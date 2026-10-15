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
            "START", "pms:project-task:execute", "CANCEL", "pms:project-task:complete");
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
        return inspect(query, null, null);
    }

    public TaskBindingInspection inspect(ProjectTaskQueryService.TaskRead read,
            cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessContext context) {
        var actor = read.actor();
        return inspect(new TaskBindingInspectionQuery(actor.tenantId(), read.task().getId(), actor.actorId(), actor.correlationId()), read, context);
    }

    private TaskBindingInspection inspect(TaskBindingInspectionQuery query, ProjectTaskQueryService.TaskRead read,
            cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessContext initialContext) {
        if (query == null || query.tenantId() == null || query.taskId() == null || query.actorId() == null) {
            return TaskBindingInspection.failed(bindingType(), "BINDING_FACT_UNKNOWN");
        }
        var context = read == null ? businessService.getContext(query.taskId(), query.tenantId(), query.actorId(), query.correlationId()) : initialContext;
        if (context == null || context.recoverableError() != null || context.factVersion() == null) {
            return TaskBindingInspection.failed(bindingType(), context == null || context.recoverableError() == null
                    ? "BINDING_FACT_UNKNOWN" : context.recoverableError());
        }
        var task = read == null ? taskMapper.selectTask(new TaskByIdQuery(query.tenantId(), query.taskId())) : read.task();
        if (task == null || !Objects.equals(task.getProjectId(), context.projectId())) {
            return TaskBindingInspection.failed(bindingType(), "BINDING_FACT_UNKNOWN");
        }
        var transitions = stateMachineMapper.selectTransitions(new TaskStateMachineRevisionLockQuery(
                query.tenantId(), task.getStateMachineRevisionId())).stream()
                .filter(transition -> PERMISSIONS.containsKey(transition.getActionCode()))
                .filter(transition -> Objects.equals(transition.getFromStatusCode(), TaskExecutionPolicy.transitionSource(
                        task.getStatus(), transition.getActionCode(), false))
                        || Objects.equals(transition.getFromStatusCode(), TaskExecutionPolicy.transitionSource(
                        task.getStatus(), transition.getActionCode(), true)))
                .toList();
        if (transitions.isEmpty()) {
            return new TaskBindingInspection(bindingType(), Set.of(), context.factVersion(), null);
        }
        boolean superAdmin = read == null && Objects.equals(query.tenantId(), cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId())
                && permissionApi.hasAnyRoles(query.actorId(), cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum.SUPER_ADMIN.getCode());
        var assignment = read == null ? assignmentMapper.selectCurrent(new CurrentTaskAssignmentsQuery(query.tenantId(),
                Set.of(task.getId()))).stream().findFirst().orElse(null) : read.assignment();
        var memberships = read == null ? memberMapper.selectActiveByUser(new ActiveProjectMemberQuery(query.tenantId(), query.actorId(), LocalDateTime.now())) : java.util.List.<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO>of();
        boolean assignee = read == null ? superAdmin || TaskExecutionPolicy.permits(task.getProjectId(), query.actorId(), assignment, memberships) : read.assignee();
        boolean manager = read == null ? superAdmin || memberships.stream().anyMatch(row ->
                Objects.equals(task.getProjectId(), row.getProjectId()) && "PROJECT_MANAGER".equals(row.getMemberRole())) : read.projectManager();
        Set<String> allowed = new HashSet<>();
        for (var transition : transitions) {
            String action = transition.getActionCode();
            if (!Objects.equals(TaskExecutionPolicy.transitionSource(task.getStatus(), action, assignment != null),
                    transition.getFromStatusCode())) continue;
            boolean execute = "START".equals(action);
            boolean roleAllowed = switch (String.valueOf(transition.getAllowedRoleCode())) {
                case "CURRENT_EFFECTIVE_ASSIGNEE" -> assignee;
                case "CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER",
                     "CURRENT_PROJECT_MANAGER_OR_AUTHORIZED_SERVICE_MANAGER_FOR_CROSS_REGION" -> manager;
                default -> false;
            };
            if (!roleAllowed || (execute ? !assignee : !manager)
                    || !(read == null ? permissionApi.hasAnyPermissions(query.actorId(), PERMISSIONS.get(action)) : read.permission(PERMISSIONS.get(action)))) continue;
            if (read != null) {
                if (read.scope(execute ? ProjectScopeApi.ACTION_EDIT : ProjectScopeApi.ACTION_MANAGE)) allowed.add(action);
                continue;
            }
            var scope = scopeApi.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(), query.actorId(),
                    task.getProjectId(), execute ? ProjectScopeApi.ACTION_EDIT : ProjectScopeApi.ACTION_MANAGE));
            if (scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(task.getProjectId())) {
                allowed.add(action);
            }
        }
        return new TaskBindingInspection(bindingType(), Set.copyOf(allowed), context.factVersion(), null);
    }
}
