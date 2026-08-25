package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.CurrentTaskAssignmentsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskByIdQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachineRevisionLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TaskNativeBindingHostProvider implements TaskBindingHostProvider {

    private static final String BINDING_TYPE = "TASK_NATIVE";
    private static final Set<String> MANAGER_ROLES = Set.of(
            "PROJECT_MANAGER", "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");
    private static final Map<String, String> ACTION_PERMISSIONS = Map.of(
            "ASSIGN", "pms:project-task:assign",
            "START", "pms:project-task:execute",
            "SUBMIT", "pms:project-task:execute",
            "COMPLETE", "pms:project-task:complete",
            "CANCEL", "pms:project-task:complete");

    private final ProjectTaskRuntimeMapper taskMapper;
    private final ProjectTaskExecutionContractMapper contractMapper;
    private final ProjectTaskAssignmentMapper assignmentMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final TaskStateMachineMapper stateMachineMapper;
    private final PermissionCommonApi permissionApi;
    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper projectTreeVersionMapper;
    private final ProjectTreeScopeService projectTreeScopeService;

    @Override
    public String bindingType() {
        return BINDING_TYPE;
    }

    @Override
    public TaskBindingInspection inspect(TaskBindingInspectionQuery query) {
        if (query == null || query.tenantId() == null || query.tenantId() < 0
                || query.taskId() == null || query.taskId() <= 0
                || query.actorId() == null || query.actorId() <= 0) {
            return TaskBindingInspection.failed(BINDING_TYPE, "BINDING_FACT_UNKNOWN");
        }
        ProjectTaskInstanceDO task = taskMapper.selectTask(new TaskByIdQuery(query.tenantId(), query.taskId()));
        ProjectTaskExecutionContractDO contract = contractMapper.selectCurrentByTaskId(query.taskId());
        if (task == null || contract == null || !Objects.equals(contract.getTenantId(), query.tenantId())) {
            return TaskBindingInspection.failed(BINDING_TYPE, "BINDING_FACT_UNKNOWN");
        }
        if (!BINDING_TYPE.equals(contract.getWorkBindingTypeCode()) || hasExternalTarget(contract)) {
            return TaskBindingInspection.failed(BINDING_TYPE, "BINDING_CONTRACT_INVALID");
        }
        ProjectTaskAssignmentDO assignment = assignmentMapper.selectCurrent(
                new CurrentTaskAssignmentsQuery(query.tenantId(), Set.of(query.taskId())))
                .stream().findFirst().orElse(null);
        Set<String> roles = actorRoles(task, assignment, query);
        List<TaskStateTransitionDO> transitions = stateMachineMapper.selectTransitions(
                new TaskStateMachineRevisionLockQuery(query.tenantId(), task.getStateMachineRevisionId()));
        Set<String> allowedActions = new HashSet<>();
        transitions.stream()
                .filter(item -> Objects.equals(item.getFromStatusCode(), task.getStatus()))
                .filter(item -> roleMatches(item.getAllowedRoleCode(), roles))
                .filter(item -> hasPermission(query.actorId(), item.getActionCode()))
                .map(TaskStateTransitionDO::getActionCode)
                .forEach(allowedActions::add);
        String factVersion = task.getVersion() + ":" + contract.getContractVersion() + ":"
                + (assignment == null ? 0 : assignment.getVersion());
        return new TaskBindingInspection(BINDING_TYPE, Set.copyOf(allowedActions), factVersion, null);
    }

    private Set<String> actorRoles(ProjectTaskInstanceDO task, ProjectTaskAssignmentDO assignment,
                                   TaskBindingInspectionQuery query) {
        Set<String> roles = new HashSet<>();
        if (assignment != null && Objects.equals(assignment.getAssigneeUserId(), query.actorId())) {
            roles.add("CURRENT_EFFECTIVE_ASSIGNEE");
        }
        List<ProjectMemberAssignmentDO> memberships = memberMapper.selectActiveByUser(
                new ActiveProjectMemberQuery(query.tenantId(), query.actorId(), LocalDateTime.now()));
        boolean projectManager = memberships.stream()
                .anyMatch(item -> Objects.equals(item.getProjectId(), task.getProjectId())
                        && "PROJECT_MANAGER".equals(item.getMemberRole()));
        boolean manager = memberships.stream()
                .anyMatch(item -> Objects.equals(item.getProjectId(), task.getProjectId())
                        && MANAGER_ROLES.contains(item.getMemberRole()));
        if (manager && hasManageScope(task, query)) {
            roles.add("CURRENT_PROJECT_MANAGER_OR_AUTHORIZED_SERVICE_MANAGER_FOR_CROSS_REGION");
        }
        if (projectManager && hasManageScope(task, query)) {
            roles.add("CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER");
        }
        return roles;
    }

    private boolean hasManageScope(ProjectTaskInstanceDO task, TaskBindingInspectionQuery query) {
        ProjectMasterDO project = projectMapper.selectById(task.getProjectId());
        if (project == null || !Objects.equals(project.getTenantId(), query.tenantId())) return false;
        long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        var version = projectTreeVersionMapper.selectLatestActive(rootId);
        if (version == null) return false;
        var scope = projectTreeScopeService.resolve(new ProjectScopeQuery(query.tenantId(), query.actorId(),
                task.getProjectId(), ProjectScopeApi.ACTION_MANAGE, version.getTreeVersion()));
        return scope.visibility(task.getProjectId()) == ProjectTreeScopeService.Visibility.FULL;
    }

    private boolean roleMatches(String allowedRole, Set<String> roles) {
        return allowedRole != null && roles.contains(allowedRole);
    }

    private boolean hasPermission(Long actorId, String actionCode) {
        String permission = ACTION_PERMISSIONS.get(actionCode);
        return permission != null && permissionApi.hasAnyPermissions(actorId, permission);
    }

    private boolean hasExternalTarget(ProjectTaskExecutionContractDO contract) {
        return contract.getTargetContextCode() != null || contract.getTargetObjectType() != null
                || contract.getTargetObjectKey() != null || contract.getComponentKey() != null
                || contract.getDynamicFormRevisionId() != null || contract.getApprovalInstanceId() != null;
    }
}
