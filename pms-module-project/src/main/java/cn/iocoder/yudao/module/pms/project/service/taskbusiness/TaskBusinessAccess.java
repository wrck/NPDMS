package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskExecutionPolicy;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.CurrentTaskAssignmentsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskByIdQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskVisibilityQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;

/** Independent of the workbench query service to avoid cyclic completion/query dependencies. */
@Component
@RequiredArgsConstructor
class TaskBusinessAccess {
    private final ProjectScopeApi scopeApi;
    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTaskAssignmentMapper assignmentMapper;
    private final ProjectTaskRuntimeMapper taskMapper;
    private final PermissionApi permissionApi;
    private final cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService treeScopes;
    private final cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectOrganizationScopeService organizationScopes;
    private final ProjectManagementQualificationApi managementAccess;

    ProjectTaskInstanceDO read(Long taskId, Long tenantId, Long actorId) {
        if (taskId == null || taskId <= 0 || tenantId == null || tenantId < 0 || actorId == null || actorId <= 0)
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        if (!permissionApi.hasAnyPermissions(actorId, "pms:project-task:query"))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var task = taskMapper.selectTask(new TaskByIdQuery(tenantId, taskId));
        if (task == null || !Objects.equals(task.getTenantId(), tenantId))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var project = projectMapper.selectById(task.getProjectId());
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)
                || !fullScope(new Context(tenantId, actorId, task.getProjectId(), taskId, null), ProjectScopeApi.ACTION_VIEW))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        if (treeScopes.isTenantSuperAdmin(tenantId, actorId)) return task;
        var memberships = memberMapper.selectAuthorizedByUser(new ActiveProjectMemberQuery(tenantId, actorId, LocalDateTime.now()));
        boolean manager = TaskExecutionPolicy.isProjectMember(task.getProjectId(), memberships)
                || regionalProject(new Context(tenantId, actorId, task.getProjectId(), taskId, null));
        if (!manager && !taskMapper.selectFullTaskIds(new TaskVisibilityQuery(tenantId, task.getProjectId(), actorId, false)).contains(taskId))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        return task;
    }

    /** Legacy association maintenance retains its original UPDATE permission. */
    boolean writable(ProjectTaskInstanceDO task, ProjectMasterDO project, Context context) {
        return permitted(new AccessQuery(task, project, context, "pms:project-task:update", false));
    }

    boolean writableLocked(ProjectTaskInstanceDO task, ProjectMasterDO project, Context context) {
        return permitted(new AccessQuery(task, project, context, "pms:project-task:update", true));
    }

    /** Executing a bound business operation is not permission to edit task metadata. */
    boolean executable(ProjectTaskInstanceDO task, ProjectMasterDO project, Context context) {
        return permitted(new AccessQuery(task, project, context, "pms:project-task:execute", false));
    }

    boolean executableLocked(ProjectTaskInstanceDO task, ProjectMasterDO project, Context context) {
        return permitted(new AccessQuery(task, project, context, "pms:project-task:execute", true));
    }

    private record AccessQuery(ProjectTaskInstanceDO task, ProjectMasterDO project, Context context, String permission, boolean locking) { }

    private boolean permitted(AccessQuery query) {
        var task = query.task(); var project = query.project(); var context = query.context();
        if (project == null || !Objects.equals(project.getTenantId(), context.tenantId())
                || !Objects.equals(project.getId(), context.projectId())
                || !Objects.equals(task.getTenantId(), context.tenantId())
                || !Objects.equals(task.getProjectId(), context.projectId())
                || !"ACTIVE".equals(project.getLifecycleStatus()) || task.getStatus() == null
                || Set.of("DONE", "CLOSED", "CANCELLED", "CANCELED").contains(task.getStatus())
                || !permissionApi.hasAnyPermissions(context.actorId(), query.permission())) return false;
        if (treeScopes.isTenantSuperAdmin(context.tenantId(), context.actorId()))
            return fullScope(context, ProjectScopeApi.ACTION_MANAGE);
        if (!fullScope(context, ProjectScopeApi.ACTION_EDIT)) return false;
        var assignment = query.locking() ? assignmentMapper.selectCurrentForUpdate(
                new cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentLockQuery(context.tenantId(), task.getId()))
                : assignmentMapper.selectCurrent(new CurrentTaskAssignmentsQuery(context.tenantId(), Set.of(task.getId())))
                    .stream().findFirst().orElse(null);
        var memberships = query.locking() ? memberMapper.selectAuthorizedByUserForUpdate(
                new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberForUpdateQuery(
                    context.tenantId(), context.projectId(), context.actorId(), LocalDateTime.now()))
                : memberMapper.selectAuthorizedByUser(new ActiveProjectMemberQuery(context.tenantId(), context.actorId(), LocalDateTime.now()));
        if (TaskExecutionPolicy.permits(task.getProjectId(), context.actorId(), assignment, memberships)) return true;
        if (assignment != null) return false;
        var regional = new ProjectManagementQualificationApi.Query(
                context.tenantId(), context.projectId(), context.actorId());
        return query.locking() ? (managementAccess.lockCurrent(new ProjectManagementQualificationApi.CurrentRevalidation(regional, project.getVersion(), "ACTIVE", null)) != null) : managementAccess.permits(regional);
    }

    /** Only this frozen planning Owner operation is added; lifecycle and association writes keep their original guards. */
    boolean planningOperationAllowed(PlanningOperationQuery query) {
        var task = query.task(); var project = query.project(); var context = query.context(); var binding = query.binding();
        if (task == null || project == null || context == null || binding == null
                || !Objects.equals(context.actorId(), cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId())
                || !Objects.equals(project.getTenantId(), context.tenantId())
                || !Objects.equals(task.getTenantId(), context.tenantId())
                || !Objects.equals(project.getId(), context.projectId())
                || !Objects.equals(task.getProjectId(), context.projectId())
                || !Objects.equals(task.getId(), context.taskId())
                || !"ACTIVE".equals(project.getLifecycleStatus())
                || task.getStatus() == null || Set.of("DONE", "CLOSED", "CANCELLED", "CANCELED").contains(task.getStatus())
                || !"PLN".equals(binding.getTargetContextCode())
                || binding.getParameters() == null
                || !("STAGE_PLAN".equals(binding.getTargetObjectType())
                        && "BUSINESS_OBJECT".equals(binding.getType())
                        && "STAGE_PLAN".equals(binding.getTargetObjectKey())
                        && "PLN_STAGE_PLAN_APPROVAL".equals(binding.getComponentKey())
                        && "REFERENCE_EXISTING".equals(binding.getParameters().path("instanceResolutionStrategy").asText())
                    || "CONSTRUCTION_PLAN".equals(binding.getTargetObjectType())
                        && ("BUSINESS_OBJECT".equals(binding.getType()) || "BUSINESS_COMPONENT".equals(binding.getType()))
                        && ("CONSTRUCTION_PLAN".equals(binding.getTargetObjectKey()) || "PROJECT_CONSTRUCTION_PLAN".equals(binding.getTargetObjectKey()))
                        && "PLN_CONSTRUCTION_PLAN".equals(binding.getComponentKey())
                        && Set.of("REFERENCE_EXISTING", "CREATE_ON_FIRST_ACTION").contains(binding.getParameters().path("instanceResolutionStrategy").asText()))
                || !permissionApi.hasAnyPermissions(context.actorId(), "pms:project-task:execute")
                || !permissionApi.hasAnyPermissions(context.actorId(), "pms:imp-stage-plan:create",
                    "pms:imp-stage-plan:update", "pms:imp-stage-plan:submit", "pms:construction-plan:duration-manage")) return false;
        return fullScope(context, ProjectScopeApi.ACTION_EDIT) && regionalProject(context);
    }

    /** Assignment opens the existing Owner maintenance flow, without granting generic task commands. */
    boolean assignmentOperationAllowed(PlanningOperationQuery query) {
        var task = query.task(); var project = query.project(); var context = query.context(); var binding = query.binding();
        return task != null && project != null && context != null && binding != null
                && Objects.equals(context.actorId(), cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId())
                && Objects.equals(project.getTenantId(), context.tenantId()) && Objects.equals(task.getTenantId(), context.tenantId())
                && Objects.equals(project.getId(), context.projectId()) && Objects.equals(task.getProjectId(), context.projectId())
                && Objects.equals(task.getId(), context.taskId()) && "ACTIVE".equals(project.getLifecycleStatus())
                && task.getStatus() != null && !Set.of("DONE", "CLOSED", "CANCELLED", "CANCELED").contains(task.getStatus())
                && "BUSINESS_OBJECT".equals(binding.getType()) && "PROJ".equals(binding.getTargetContextCode())
                && "PROJECT_ASSIGNMENT".equals(binding.getTargetObjectType())
                && ("PROJECT_ASSIGNMENT".equals(binding.getTargetObjectKey()) || "PROJECT_PROJECT_ASSIGNMENT".equals(binding.getTargetObjectKey()))
                && "PROJ_PROJECT_ASSIGNMENT".equals(binding.getComponentKey()) && binding.getParameters() != null
                && "REFERENCE_EXISTING".equals(binding.getParameters().path("instanceResolutionStrategy").asText())
                && permissionApi.hasAnyPermissions(context.actorId(), "pms:project:assign", "pms:project-team:create")
                && fullScope(context, ProjectScopeApi.ACTION_MANAGE);
    }

    record PlanningOperationQuery(ProjectTaskInstanceDO task, ProjectMasterDO project, Context context,
            cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract binding) {
        static PlanningOperationQuery fromContract(ProjectTaskInstanceDO task, ProjectMasterDO project, Context context,
                cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO contract) {
            if (contract == null || !Objects.equals(contract.getTenantId(), context.tenantId())
                    || !Objects.equals(contract.getProjectTaskId(), context.taskId())) return new PlanningOperationQuery(task, project, context, null);
            // Callers have already validated the current frozen task contract and its metadata.
            var frozen = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract();
            frozen.setType(contract.getWorkBindingTypeCode()); frozen.setTargetContextCode(contract.getTargetContextCode());
            frozen.setTargetObjectType(contract.getTargetObjectType()); frozen.setTargetObjectKey(contract.getTargetObjectKey());
            frozen.setComponentKey(contract.getComponentKey());
            var metadata = TaskBusinessBinding.parse(contract);
            frozen.setParameters(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                    cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                            java.util.Map.of("instanceResolutionStrategy", metadata.instanceResolutionStrategy()))));
            return new PlanningOperationQuery(task, project, context, frozen);
        }
    }

    private boolean regionalProject(Context context) {
        return organizationScopes.projectIds(new cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectOrganizationScopeService.Query(
                context.tenantId(), context.actorId(), ProjectScopeApi.ACTION_MANAGE, false, Set.of(context.projectId())))
                .contains(context.projectId());
    }

    ProjectMasterDO project(Long id) { return projectMapper.selectById(id); }

    private boolean fullScope(Context context, String action) {
        var scope = scopeApi.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(context.tenantId())
                .subjectUserId(context.actorId())
                .anchorProjectId(context.projectId())
                .actionCode(action).build());
        return scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(context.projectId());
    }
}
