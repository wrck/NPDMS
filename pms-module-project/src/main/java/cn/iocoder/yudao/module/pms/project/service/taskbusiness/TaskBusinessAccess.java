package cn.iocoder.yudao.module.pms.project.service.taskbusiness;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;

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
        var memberships = memberMapper.selectActiveByUser(new ActiveProjectMemberQuery(tenantId, actorId, LocalDateTime.now()));
        boolean manager = memberships.stream().anyMatch(m -> Objects.equals(m.getProjectId(), task.getProjectId())
                && ProjectMemberRoles.MANAGEMENT_CODES.contains(m.getMemberRole()));
        if (!manager && !taskMapper.selectFullTaskIds(new TaskVisibilityQuery(tenantId, task.getProjectId(), actorId, false)).contains(taskId))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        return task;
    }

    boolean writable(ProjectTaskInstanceDO task, ProjectMasterDO project, Context context) {
        if (project == null || !Objects.equals(project.getTenantId(), context.tenantId())
                || !Objects.equals(project.getId(), context.projectId())
                || !Objects.equals(task.getTenantId(), context.tenantId())
                || !Objects.equals(task.getProjectId(), context.projectId())
                || !"ACTIVE".equals(project.getLifecycleStatus()) || task.getStatus() == null
                || Set.of("DONE", "CLOSED", "CANCELLED", "CANCELED").contains(task.getStatus())
                || !permissionApi.hasAnyPermissions(context.actorId(), "pms:project-task:update")) return false;
        if (treeScopes.isTenantSuperAdmin(context.tenantId(), context.actorId()))
            return fullScope(context, ProjectScopeApi.ACTION_MANAGE);
        boolean assigned = assignmentMapper.selectCurrent(new CurrentTaskAssignmentsQuery(context.tenantId(), Set.of(task.getId())))
                .stream().anyMatch(a -> Objects.equals(a.getTenantId(), context.tenantId())
                        && Objects.equals(a.getProjectTaskId(), task.getId())
                        && Objects.equals(a.getAssigneeUserId(), context.actorId()));
        if (assigned && fullScope(context, ProjectScopeApi.ACTION_EDIT)) return true;
        boolean pm = memberMapper.selectActiveByUser(new ActiveProjectMemberQuery(context.tenantId(), context.actorId(), LocalDateTime.now()))
                .stream().anyMatch(m -> Objects.equals(m.getProjectId(), context.projectId())
                        && "PROJECT_MANAGER".equals(m.getMemberRole()));
        return pm && fullScope(context, ProjectScopeApi.ACTION_MANAGE);
    }

    ProjectMasterDO project(Long id) { return projectMapper.selectById(id); }

    private boolean fullScope(Context context, String action) {
        var scope = scopeApi.resolveCurrent(new ProjectCurrentScopeQuery(context.tenantId(), context.actorId(), context.projectId(), action));
        return scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(context.projectId());
    }
}
