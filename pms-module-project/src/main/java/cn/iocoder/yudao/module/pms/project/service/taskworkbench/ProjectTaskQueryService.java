package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskDetailRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskNodeRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskTreeQueryReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskTreeRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskWorkbenchRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectWorkspaceRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStageCount;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.CurrentTaskAssignmentsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAncestorBatchQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskByIdQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskVisibilityQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_QUERY_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class ProjectTaskQueryService {

    private static final Set<String> MANAGER_ROLES = Set.of(
            "PROJECT_MANAGER", "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");
    private static final List<String> OVERVIEW_TABS = List.of(
            "BASIC_INFO", "PROJECT_TREE", "TEAM_MEMBERS", "PROJECT_TASKS", "DEVICES", "IMPLEMENTATION_SCOPE");

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper projectTreeVersionMapper;
    private final ProjectTreeScopeService projectTreeScopeService;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectStageInstanceMapper stageMapper;
    private final ProjectTaskRuntimeMapper taskMapper;
    private final ProjectTaskAssignmentMapper assignmentMapper;
    private final ProjectTaskExecutionContractMapper contractMapper;
    private final TaskBindingHostRegistry bindingRegistry;
    private final PermissionCommonApi permissionApi;

    public ProjectWorkspaceRespVO getWorkspace(Long projectId, TaskWorkbenchActor actor) {
        TaskAccess access = resolveAccess(projectId, actor, true);
        Map<String, Long> counts = taskMapper.selectStageCounts(access.visibilityQuery()).stream()
                .collect(Collectors.toMap(TaskStageCount::stageCode, TaskStageCount::taskCount));
        List<ProjectWorkspaceRespVO.StageTaskNavigation> navigation = stageMapper.selectListByProjectId(projectId)
                .stream().map(stage -> stageNavigation(stage, counts)).toList();
        ProjectWorkspaceRespVO response = new ProjectWorkspaceRespVO();
        response.setProjectId(projectId);
        response.setProjectCode(access.project().getProjectCode());
        response.setProjectName(access.project().getProjectName());
        response.setOverviewTabs(OVERVIEW_TABS);
        response.setStageTaskNavigation(navigation);
        response.setTaskTreeVersion(access.project().getTaskTreeVersion());
        response.setProjectionWatermark(watermark(access.project()));
        response.setAllowedActions(workspaceAllowedActions(access, actor));
        return response;
    }

    public ProjectTaskTreeRespVO getTasks(Long projectId, ProjectTaskTreeQueryReqVO request,
                                          TaskWorkbenchActor actor) {
        TaskAccess access = resolveAccess(projectId, actor, false);
        if (access == null) {
            return new ProjectTaskTreeRespVO(List.of(), null, 0L, null);
        }
        ProjectTaskTreeQuery.Mode mode = parseMode(request.getMode());
        validateMode(mode, request);
        Cursor cursor = parseCursor(request.getCursor());
        int pageSize = request.getPageSize() == null ? 50 : request.getPageSize();
        List<ProjectTaskInstanceDO> tasks = taskMapper.selectTree(ProjectTaskTreeQuery.builder()
                .tenantId(actor.tenantId()).projectIds(Set.of(projectId)).visibilityQuery(access.visibilityQuery())
                .mode(mode).parentTaskId(request.getParentTaskId()).targetTaskId(request.getTaskId())
                .businessLevelCode(trimToNull(request.getBusinessLevelCode()))
                .stageCode(trimToNull(request.getStageCode()))
                .keyword(trimToNull(request.getKeyword())).cursorSortOrder(cursor.sortOrder())
                .cursorTaskId(cursor.taskId()).pageSize(pageSize + 1).build());
        boolean hasMore = tasks.size() > pageSize;
        List<ProjectTaskInstanceDO> page = hasMore ? tasks.subList(0, pageSize) : tasks;
        if (mode == ProjectTaskTreeQuery.Mode.LOCATE) {
            page = withLocateAncestors(page, access);
        }
        Map<Long, Long> assignees = currentAssignees(actor.tenantId(), page);
        List<ProjectTaskNodeRespVO> rows = page.stream()
                .map(task -> toNode(task, access.fullProjectAccess()
                        || access.fullTaskIds().contains(task.getId()), assignees.get(task.getId())))
                .toList();
        String nextCursor = hasMore ? encodeCursor(tasks.get(pageSize - 1)) : null;
        return new ProjectTaskTreeRespVO(rows, nextCursor, access.project().getTaskTreeVersion(),
                watermark(access.project()));
    }

    public ProjectTaskDetailRespVO getTask(Long taskId, TaskWorkbenchActor actor) {
        TaskAndAccess value = requireFullTask(taskId, actor);
        ProjectTaskAssignmentDO assignment = assignmentMapper.selectCurrent(
                new CurrentTaskAssignmentsQuery(actor.tenantId(), Set.of(taskId))).stream().findFirst().orElse(null);
        return toDetail(value.task(), assignment == null ? null : assignment.getAssigneeUserId());
    }

    public ProjectTaskWorkbenchRespVO getWorkbench(Long taskId, TaskWorkbenchActor actor) {
        TaskAndAccess value = requireFullTask(taskId, actor);
        ProjectTaskExecutionContractDO contract = contractMapper.selectCurrentByTaskId(taskId);
        ProjectTaskWorkbenchRespVO response = new ProjectTaskWorkbenchRespVO();
        response.setTask(getTask(taskId, actor));
        if (contract == null || !Objects.equals(contract.getTenantId(), actor.tenantId())) {
            response.setAllowedActions(Set.of());
            response.setRecoverableError("BINDING_FACT_UNKNOWN");
            return response;
        }
        TaskBindingInspection inspection = bindingRegistry.inspect(contract.getWorkBindingTypeCode(),
                new TaskBindingInspectionQuery(actor.tenantId(), taskId, actor.actorId(), actor.correlationId()));
        response.setExecutionContractId(contract.getId());
        response.setContractVersion(contract.getContractVersion());
        response.setBindingType(contract.getWorkBindingTypeCode());
        response.setTrustedTargetRef(trustedTargetRef(contract));
        response.setAllowedActions(workbenchAllowedActions(value, inspection.allowedActions(), actor));
        response.setFactVersion(inspection.factVersion());
        response.setRecoverableError(inspection.recoverableError());
        return response;
    }

    private TaskAccess resolveAccess(Long projectId, TaskWorkbenchActor actor, boolean requireProject) {
        validateActor(actor);
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) {
            if (requireProject) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            return null;
        }
        long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        ProjectTreeVersionDO treeVersion = projectTreeVersionMapper.selectLatestActive(rootId);
        if (treeVersion == null) {
            if (requireProject) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            return null;
        }
        ProjectTreeScopeService.ProjectTreeScope projectScope = projectTreeScopeService.resolve(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_VIEW, treeVersion.getTreeVersion()));
        if (projectScope.visibility(projectId) != ProjectTreeScopeService.Visibility.FULL) {
            if (requireProject) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            return null;
        }
        List<ProjectMemberAssignmentDO> memberships = memberMapper.selectActiveByUser(new ActiveProjectMemberQuery(
                actor.tenantId(), actor.actorId(), LocalDateTime.now()));
        boolean fullProjectAccess = memberships.stream().anyMatch(item -> Objects.equals(item.getProjectId(), projectId)
                && MANAGER_ROLES.contains(item.getMemberRole()));
        boolean projectManager = memberships.stream().anyMatch(item -> Objects.equals(item.getProjectId(), projectId)
                && "PROJECT_MANAGER".equals(item.getMemberRole()));
        TaskVisibilityQuery visibilityQuery = new TaskVisibilityQuery(
                actor.tenantId(), projectId, actor.actorId(), fullProjectAccess);
        Set<Long> full = fullProjectAccess
                ? Set.of() : Set.copyOf(taskMapper.selectFullTaskIds(visibilityQuery));
        return new TaskAccess(project, visibilityQuery, fullProjectAccess, full, projectManager,
                treeVersion.getTreeVersion());
    }

    private TaskAndAccess requireFullTask(Long taskId, TaskWorkbenchActor actor) {
        validateActor(actor);
        ProjectTaskInstanceDO task = taskMapper.selectTask(new TaskByIdQuery(actor.tenantId(), taskId));
        if (task == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        TaskAccess access = resolveAccess(task.getProjectId(), actor, true);
        if (!access.fullProjectAccess() && !access.fullTaskIds().contains(taskId)) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
        return new TaskAndAccess(task, access);
    }

    private List<ProjectTaskInstanceDO> withLocateAncestors(List<ProjectTaskInstanceDO> matches,
                                                             TaskAccess access) {
        Map<Long, ProjectTaskInstanceDO> rows = new LinkedHashMap<>();
        Set<Long> matchIds = matches.stream().map(ProjectTaskInstanceDO::getId).collect(Collectors.toSet());
        TaskVisibilityQuery visibility = access.visibilityQuery();
        taskMapper.selectAncestors(new TaskAncestorBatchQuery(
                        visibility.tenantId(), visibility.projectId(), visibility.actorId(),
                        visibility.fullProjectAccess(), matchIds))
                .forEach(item -> rows.put(item.getId(), item));
        matches.forEach(item -> rows.put(item.getId(), item));
        return rows.values().stream()
                .sorted(Comparator.comparing(ProjectTaskInstanceDO::getTreeDepth)
                        .thenComparing(item -> item.getSortOrder() == null ? 0 : item.getSortOrder())
                        .thenComparing(ProjectTaskInstanceDO::getId))
                .toList();
    }

    private Map<Long, Long> currentAssignees(Long tenantId, List<ProjectTaskInstanceDO> tasks) {
        Set<Long> ids = tasks.stream().map(ProjectTaskInstanceDO::getId).collect(Collectors.toSet());
        return assignmentMapper.selectCurrent(new CurrentTaskAssignmentsQuery(tenantId, ids)).stream()
                .collect(Collectors.toMap(ProjectTaskAssignmentDO::getProjectTaskId,
                        ProjectTaskAssignmentDO::getAssigneeUserId, (left, right) -> left));
    }

    private ProjectTaskNodeRespVO toNode(ProjectTaskInstanceDO task, boolean full, Long assigneeId) {
        ProjectTaskNodeRespVO response = new ProjectTaskNodeRespVO();
        response.setTaskId(task.getId());
        response.setParentTaskId(task.getParentTaskId());
        response.setTreeDepth(task.getTreeDepth());
        response.setPlaceholder(!full);
        if (!full) return response;
        response.setProjectId(task.getProjectId());
        response.setRootTaskId(task.getRootTaskId());
        response.setTaskCode(task.getTaskCode());
        response.setName(task.getName());
        response.setStageCode(task.getStageCode());
        response.setBusinessLevelCode(task.getBusinessLevelCode());
        response.setStatus(task.getStatus());
        response.setPriority(task.getPriority());
        response.setSortOrder(task.getSortOrder());
        response.setProgress(task.getProgress());
        response.setPlanStartTime(task.getPlanStartTime());
        response.setPlanEndTime(task.getPlanEndTime());
        response.setAssigneeUserId(assigneeId);
        response.setDescription(task.getDescription());
        response.setVersion(task.getVersion());
        return response;
    }

    private ProjectTaskDetailRespVO toDetail(ProjectTaskInstanceDO task, Long assigneeId) {
        ProjectTaskDetailRespVO response = new ProjectTaskDetailRespVO();
        response.setTaskId(task.getId());
        response.setProjectId(task.getProjectId());
        response.setTaskCode(task.getTaskCode());
        response.setName(task.getName());
        response.setParentTaskId(task.getParentTaskId());
        response.setRootTaskId(task.getRootTaskId());
        response.setTreeDepth(task.getTreeDepth());
        response.setBusinessLevelCode(task.getBusinessLevelCode());
        response.setStageCode(task.getStageCode());
        response.setStatus(task.getStatus());
        response.setPriority(task.getPriority());
        response.setSortOrder(task.getSortOrder());
        response.setProgress(task.getProgress());
        response.setEstimatedHours(task.getEstimatedHours());
        response.setPlanStartTime(task.getPlanStartTime());
        response.setPlanEndTime(task.getPlanEndTime());
        response.setActualStartTime(task.getActualStartTime());
        response.setActualEndTime(task.getActualEndTime());
        response.setDescription(task.getDescription());
        response.setAssigneeUserId(assigneeId);
        response.setVersion(task.getVersion());
        return response;
    }

    private ProjectWorkspaceRespVO.StageTaskNavigation stageNavigation(ProjectStageInstanceDO stage,
                                                                        Map<String, Long> counts) {
        return new ProjectWorkspaceRespVO.StageTaskNavigation(stage.getStageCode(), stage.getName(),
                stage.getStatus(), counts.getOrDefault(stage.getStageCode(), 0L));
    }

    private String trustedTargetRef(ProjectTaskExecutionContractDO contract) {
        if ("TASK_NATIVE".equals(contract.getWorkBindingTypeCode())) return null;
        if (contract.getTargetContextCode() == null || contract.getTargetObjectType() == null
                || contract.getTargetObjectKey() == null) return null;
        return contract.getTargetContextCode() + ":" + contract.getTargetObjectType() + ":"
                + contract.getTargetObjectKey();
    }

    private Set<String> workspaceAllowedActions(TaskAccess access, TaskWorkbenchActor actor) {
        if (!activeProjectManagerWithManageScope(access, actor)
                || !hasPermission(actor.actorId(), "pms:project-task:create")) return Set.of();
        return Set.of("CREATE");
    }

    private Set<String> workbenchAllowedActions(TaskAndAccess value, Set<String> bindingActions,
                                                TaskWorkbenchActor actor) {
        Set<String> actions = new HashSet<>(bindingActions);
        if (!activeProjectManagerWithManageScope(value.access(), actor)
                || Set.of("DONE", "CLOSED").contains(value.task().getStatus())) return Set.copyOf(actions);
        if (hasPermission(actor.actorId(), "pms:project-task:update")) actions.add("UPDATE");
        if (hasPermission(actor.actorId(), "pms:project-task:move")) actions.add("MOVE");
        return Set.copyOf(actions);
    }

    private boolean activeProjectManagerWithManageScope(TaskAccess access, TaskWorkbenchActor actor) {
        if (!access.projectManager() || !"ACTIVE".equals(access.project().getLifecycleStatus())) return false;
        ProjectTreeScopeService.ProjectTreeScope scope = projectTreeScopeService.resolve(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), access.project().getId(), ProjectScopeApi.ACTION_MANAGE,
                access.projectTreeVersion()));
        return scope.visibility(access.project().getId()) == ProjectTreeScopeService.Visibility.FULL;
    }

    private boolean hasPermission(Long actorId, String permission) {
        return permissionApi.hasAnyPermissions(actorId, permission);
    }

    private ProjectTaskTreeQuery.Mode parseMode(String value) {
        try {
            return ProjectTaskTreeQuery.Mode.valueOf(value == null ? "DIRECT_CHILDREN" : value.trim());
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private void validateMode(ProjectTaskTreeQuery.Mode mode, ProjectTaskTreeQueryReqVO request) {
        if (request == null || mode == ProjectTaskTreeQuery.Mode.ALL_DESCENDANTS && request.getTaskId() == null
                || mode == ProjectTaskTreeQuery.Mode.ANCESTOR_CHAIN && request.getTaskId() == null
                || mode == ProjectTaskTreeQuery.Mode.BUSINESS_LEVEL
                    && trimToNull(request.getBusinessLevelCode()) == null
                || mode == ProjectTaskTreeQuery.Mode.LOCATE
                    && request.getTaskId() == null && trimToNull(request.getKeyword()) == null) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private Cursor parseCursor(String value) {
        if (value == null || value.isBlank()) return new Cursor(null, null);
        String[] parts = value.trim().split(":", -1);
        try {
            if (parts.length != 2) throw new NumberFormatException();
            return new Cursor(Integer.valueOf(parts[0]), Long.valueOf(parts[1]));
        } catch (NumberFormatException ex) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private String encodeCursor(ProjectTaskInstanceDO task) {
        return (task.getSortOrder() == null ? 0 : task.getSortOrder()) + ":" + task.getId();
    }

    private String watermark(ProjectMasterDO project) {
        return "TASK_TREE:" + project.getId() + ":" + project.getTaskTreeVersion();
    }

    private void validateActor(TaskWorkbenchActor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record Cursor(Integer sortOrder, Long taskId) {}
    private record TaskAccess(ProjectMasterDO project, TaskVisibilityQuery visibilityQuery,
                              boolean fullProjectAccess, Set<Long> fullTaskIds, boolean projectManager,
                              long projectTreeVersion) {}
    private record TaskAndAccess(ProjectTaskInstanceDO task, TaskAccess access) {}
}
