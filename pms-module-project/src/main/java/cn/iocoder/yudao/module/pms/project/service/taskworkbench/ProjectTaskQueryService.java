package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService;
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
    @jakarta.annotation.Resource
    private ProjectTaskApprovalService taskApprovals;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService stageAdmission;

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
    private final cn.iocoder.yudao.module.pms.project.service.operation.ProjectResultSubscriptionObservationQuery subscriptionObservations;
    private final ProjectDeliverableInitializationApplicationService deliverableInitializationApplicationService;
    private final PermissionCommonApi permissionApi;

    public ProjectWorkspaceRespVO getWorkspace(Long projectId, TaskWorkbenchActor actor) {
        TaskAccess access = resolveAccess(projectId, actor, true);
        return workspace(access, actor, null);
    }

    public ProjectWorkspaceRespVO getWorkspace(TaskRead read) {
        return workspace(read.value.access(), read.actor, read);
    }

    private ProjectWorkspaceRespVO workspace(TaskAccess access, TaskWorkbenchActor actor, TaskRead read) {
        Long projectId = access.project().getId();
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
        response.setAllowedActions(workspaceAllowedActions(access, actor, read));
        response.setDescriptionLimit(TaskRichText.MAX_LENGTH);
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
                .status(trimToNull(request.getStatus())).responsibleUserId(request.getResponsibleUserId()).executorUserId(request.getExecutorUserId())
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
        return taskDetail(requireFullTask(taskId, actor), actor);
    }

    private ProjectTaskDetailRespVO taskDetail(TaskAndAccess value, TaskWorkbenchActor actor) {
        ProjectTaskAssignmentDO assignment = assignmentMapper.selectCurrent(
                new CurrentTaskAssignmentsQuery(actor.tenantId(), Set.of(value.task().getId()))).stream().findFirst().orElse(null);
        return toDetail(value.task(), assignment == null ? null : assignment.getAssigneeUserId());
    }

    /** Business-task maintenance needs task-scope grants, not Owner form/file completion facts. */
    public TaskMaintenanceAccess getMaintenanceAccess(Long taskId, TaskWorkbenchActor actor) {
        TaskAndAccess value = requireFullTask(taskId, actor);
        var contract = contractMapper.selectCurrentByTaskId(taskId);
        if (contract == null || !Objects.equals(contract.getTenantId(), actor.tenantId())) {
            return new TaskMaintenanceAccess(value.task().getVersion(), false, false);
        }
        // Native providers can grant ASSIGN to service managers independently of the project-manager grants.
        var bindingType = contract.getWorkBindingTypeCode();
        var bindingActions = bindingType != null && TaskBusinessBindingHostProvider.TYPES.contains(bindingType)
                ? Set.<String>of() : bindingRegistry.inspect(bindingType,
                new TaskBindingInspectionQuery(actor.tenantId(), taskId, actor.actorId(), actor.correlationId())).allowedActions();
        var actions = workbenchAllowedActions(value, bindingActions, actor);
        return new TaskMaintenanceAccess(value.task().getVersion(), actions.contains("ASSIGN"), actions.contains("UPDATE"));
    }

    public record TaskMaintenanceAccess(Integer taskVersion, boolean canAssign, boolean canUpdate) { }

    public ProjectTaskWorkbenchRespVO getWorkbench(Long taskId, TaskWorkbenchActor actor) {
        TaskAndAccess value = requireFullTask(taskId, actor);
        ProjectTaskExecutionContractDO contract = contractMapper.selectCurrentByTaskId(taskId);
        return workbench(value, actor, contract, null, null);
    }

    public ProjectTaskWorkbenchRespVO openingWorkbench(TaskRead read, TaskBindingInspection inspection) {
        return workbench(read.value, read.actor, read.contract, inspection, read);
    }

    private ProjectTaskWorkbenchRespVO workbench(TaskAndAccess value, TaskWorkbenchActor actor,
            ProjectTaskExecutionContractDO contract, TaskBindingInspection initialInspection, TaskRead read) {
        Long taskId = value.task().getId();
        ProjectTaskWorkbenchRespVO response = new ProjectTaskWorkbenchRespVO();
        response.setTask(read == null ? taskDetail(value, actor)
                : toDetail(read.task(), read.assignment == null ? null : read.assignment.getAssigneeUserId()));
        response.setDeliverables(taskDeliverables(value.task()));
        if (contract == null || !Objects.equals(contract.getTenantId(), actor.tenantId())) {
            response.setAllowedActions(Set.of());
            response.setRecoverableError("BINDING_FACT_UNKNOWN");
            return response;
        }
        TaskBindingInspection inspection = initialInspection != null ? initialInspection : bindingRegistry.inspect(contract.getWorkBindingTypeCode(),
                new TaskBindingInspectionQuery(actor.tenantId(), taskId, actor.actorId(), actor.correlationId()));
        response.setExecutionContractId(contract.getId());
        response.setContractVersion(contract.getContractVersion());
        response.setBindingType(contract.getWorkBindingTypeCode());
        response.setTrustedTargetRef(trustedTargetRef(contract));
        response.setAllowedActions(workbenchAllowedActions(value, inspection.allowedActions(), actor, read));
        response.setFactVersion(inspection.factVersion());
        response.setRecoverableError(inspection.recoverableError());
        if ("RESULT_SUBSCRIPTION".equals(contract.getWorkBindingTypeCode())) {
            try {
                response.setResultSubscriptions(subscriptionObservations.forNode(
                        actor.tenantId(), value.task().getProjectId(), "TASK", taskId));
            } catch (RuntimeException unavailable) {
                response.setRecoverableError("RESULT_SUBSCRIPTION_FACT_UNAVAILABLE");
            }
        }
        if ("APPROVAL".equals(contract.getWorkBindingTypeCode())) {
            if (response.getAllowedActions().contains("START")) {
                var admission = stageAdmission.previewTaskAdmission(value.access().project(), value.task(), contract);
                response.setStartAdmission(value.access().fullProjectAccess() ? admission
                        : new cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService.TaskAdmissionView(
                                admission.outcome(), List.of()));
                if (admission.outcome() != cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.Outcome.MATCHED) {
                    var allowed = new HashSet<>(response.getAllowedActions());
                    allowed.remove("START");
                    response.setAllowedActions(Set.copyOf(allowed));
                }
            }
            try { response.setApproval(taskApprovals.view(actor.tenantId(), value.task().getProjectId(), taskId, contract)); }
            catch (RuntimeException unavailable) { response.setRecoverableError("TASK_APPROVAL_FACT_UNAVAILABLE"); }
        }
        return response;
    }

    /** Server-created observation shared only by the task opening query, never by command execution. */
    public TaskRead openTaskRead(Long taskId, TaskWorkbenchActor actor) {
        validateActor(actor);
        if (taskId == null || taskId <= 0 || !hasPermission(actor.actorId(), "pms:project-task:query"))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var value = requireFullTask(taskId, actor);
        if (!Objects.equals(value.task().getTenantId(), actor.tenantId()) || !Objects.equals(value.task().getId(), taskId))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var contract = contractMapper.selectCurrentByTaskId(taskId);
        if (contract != null && (!Objects.equals(contract.getTenantId(), actor.tenantId())
                || !Objects.equals(contract.getProjectTaskId(), taskId) || contract.getEffectiveTo() != null))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var assignment = assignmentMapper.selectCurrent(new CurrentTaskAssignmentsQuery(actor.tenantId(), Set.of(taskId)))
                .stream().findFirst().orElse(null);
        return new TaskRead(value, actor, contract, assignment);
    }

    public final class TaskRead {
        private final TaskAndAccess value;
        private final TaskWorkbenchActor actor;
        private final ProjectTaskExecutionContractDO contract;
        private final ProjectTaskAssignmentDO assignment;
        private final Map<String, Boolean> permissions = new java.util.HashMap<>();
        private final Map<String, Boolean> scopes = new java.util.HashMap<>();
        private TaskRead(TaskAndAccess value, TaskWorkbenchActor actor, ProjectTaskExecutionContractDO contract,
                         ProjectTaskAssignmentDO assignment) {
            this.value = value; this.actor = actor; this.contract = contract; this.assignment = assignment;
            permissions.put("pms:project-task:query", true); scopes.put(ProjectScopeApi.ACTION_VIEW, true);
        }
        public ProjectTaskInstanceDO task() { return value.task(); }
        public ProjectMasterDO project() { return value.access().project(); }
        public ProjectTaskExecutionContractDO contract() { return contract; }
        public TaskWorkbenchActor actor() { return actor; }
        public ProjectTaskAssignmentDO assignment() { return assignment; }
        public boolean projectManager() { return value.access().projectManager(); }
        public boolean assignee() { return value.access().superAdmin() || TaskExecutionPolicy.permits(
                project().getId(), actor.actorId(), assignment, value.access().memberships()); }
        public boolean permission(String permission) {
            return permissions.computeIfAbsent(permission, code -> hasPermission(actor.actorId(), code));
        }
        public boolean scope(String action) {
            return scopes.computeIfAbsent(action, code -> projectTreeScopeService.resolve(new ProjectScopeQuery(
                    actor.tenantId(), actor.actorId(), project().getId(), code, value.access().projectTreeVersion()))
                    .visibility(project().getId()) == ProjectTreeScopeService.Visibility.FULL);
        }
        public boolean permitted(String permission) {
            return "ACTIVE".equals(project().getLifecycleStatus()) && task().getStatus() != null
                    && !Set.of("DONE", "CLOSED", "CANCELLED", "CANCELED").contains(task().getStatus())
                    && permission(permission) && assignee()
                    && scope(value.access().superAdmin() ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_EDIT);
        }
        public void requireCaller(Long tenantId, Long actorId) {
            if (!Objects.equals(actor.tenantId(), tenantId) || !Objects.equals(actor.actorId(), actorId))
                throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
    }

    /** 交付件为模板冻结的应交清单：按任务码过滤，阶段级（无任务码）行不属于任何任务视图。 */
    private java.util.List<ProjectTaskWorkbenchRespVO.TaskDeliverableItem> taskDeliverables(ProjectTaskInstanceDO task) {
        if (task.getCode() == null) return List.of();
        return deliverableInitializationApplicationService.getByProjectId(task.getProjectId()).stream()
                .filter(row -> task.getCode().equals(row.taskCode()))
                .map(row -> {
                    var item = new ProjectTaskWorkbenchRespVO.TaskDeliverableItem();
                    item.setId(row.id());
                    item.setDeliverableCode(row.deliverableCode());
                    item.setName(row.name());
                    item.setRequired(row.required());
                    item.setStatus(row.status());
                    return item;
                }).toList();
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
        boolean superAdmin = projectTreeScopeService.isTenantSuperAdmin(actor.tenantId(), actor.actorId());
        boolean fullProjectAccess = superAdmin || TaskExecutionPolicy.isProjectMember(projectId, memberships);
        boolean projectManager = superAdmin || memberships.stream().anyMatch(item -> Objects.equals(item.getProjectId(), projectId)
                && "PROJECT_MANAGER".equals(item.getMemberRole()));
        TaskVisibilityQuery visibilityQuery = new TaskVisibilityQuery(
                actor.tenantId(), projectId, actor.actorId(), fullProjectAccess);
        Set<Long> full = fullProjectAccess
                ? Set.of() : Set.copyOf(taskMapper.selectFullTaskIds(visibilityQuery));
        return new TaskAccess(project, visibilityQuery, fullProjectAccess, full, projectManager,
                treeVersion.getTreeVersion(), superAdmin, List.copyOf(memberships));
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
        response.setTaskCode(task.getCode());
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
        response.setTaskCode(task.getCode());
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
        return new ProjectWorkspaceRespVO.StageTaskNavigation(stage.getCode(), stage.getName(),
                stage.getStatus(), counts.getOrDefault(stage.getCode(), 0L));
    }

    private String trustedTargetRef(ProjectTaskExecutionContractDO contract) {
        if ("TASK_NATIVE".equals(contract.getWorkBindingTypeCode())) return null;
        // PAGE 的可信目标即冻结的路由入口；仅用于导航，不产生任何完成事实。
        if ("PAGE".equals(contract.getWorkBindingTypeCode()))
            return JsonUtils.parseTree(contract.getBindingParameterSnapshot()).path("routePath").asText(null);
        if (contract.getTargetContextCode() == null || contract.getTargetObjectType() == null
                || contract.getTargetObjectKey() == null) return null;
        return contract.getTargetContextCode() + ":" + contract.getTargetObjectType() + ":"
                + contract.getTargetObjectKey();
    }

    private Set<String> workspaceAllowedActions(TaskAccess access, TaskWorkbenchActor actor) {
        return workspaceAllowedActions(access, actor, null);
    }
    private Set<String> workspaceAllowedActions(TaskAccess access, TaskWorkbenchActor actor, TaskRead read) {
        if (access.project().getActivePlanVersionId() != null) {
            if (!"ACTIVE".equals(access.project().getLifecycleStatus())
                    || !(read == null ? hasPermission(actor.actorId(), "pms:project-plan:manage")
                    : read.permission("pms:project-plan:manage"))) return Set.of();
            if (read != null) return read.scope(ProjectScopeApi.ACTION_EDIT) ? Set.of("MANAGE_PLAN") : Set.of();
            var scope = projectTreeScopeService.resolve(new ProjectScopeQuery(actor.tenantId(), actor.actorId(),
                    access.project().getId(), ProjectScopeApi.ACTION_EDIT, access.projectTreeVersion()));
            return scope.visibility(access.project().getId()) == ProjectTreeScopeService.Visibility.FULL
                    ? Set.of("MANAGE_PLAN") : Set.of();
        }
        if (!activeProjectManagerWithManageScope(access, actor, read)
                || !(read == null ? hasPermission(actor.actorId(), "pms:project-task:create")
                : read.permission("pms:project-task:create"))) return Set.of();
        return Set.of("CREATE");
    }

    private Set<String> workbenchAllowedActions(TaskAndAccess value, Set<String> bindingActions,
                                                TaskWorkbenchActor actor) {
        return workbenchAllowedActions(value, bindingActions, actor, null);
    }
    private Set<String> workbenchAllowedActions(TaskAndAccess value, Set<String> bindingActions,
                                                TaskWorkbenchActor actor, TaskRead read) {
        Set<String> actions = new HashSet<>(bindingActions);
        if (!activeProjectManagerWithManageScope(value.access(), actor, read)
                || Set.of("DONE", "CLOSED").contains(value.task().getStatus())) return Set.copyOf(actions);
        java.util.function.Predicate<String> permission = code -> read == null ? hasPermission(actor.actorId(), code) : read.permission(code);
        if (permission.test("pms:project-task:update")) actions.add("UPDATE");
        if (value.access().project().getActivePlanVersionId() == null
                && permission.test("pms:project-task:move")) actions.add("MOVE");
        if (permission.test("pms:project-task:assign")) actions.add("ASSIGN");
        return Set.copyOf(actions);
    }

    private boolean activeProjectManagerWithManageScope(TaskAccess access, TaskWorkbenchActor actor) {
        return activeProjectManagerWithManageScope(access, actor, null);
    }
    private boolean activeProjectManagerWithManageScope(TaskAccess access, TaskWorkbenchActor actor, TaskRead read) {
        if (!access.projectManager() || !"ACTIVE".equals(access.project().getLifecycleStatus())) return false;
        if (read != null) return read.scope(ProjectScopeApi.ACTION_MANAGE);
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
                    && request.getTaskId() == null && trimToNull(request.getKeyword()) == null
                    && trimToNull(request.getStatus()) == null && request.getResponsibleUserId() == null && request.getExecutorUserId() == null) {
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
                              long projectTreeVersion, boolean superAdmin, List<ProjectMemberAssignmentDO> memberships) {}
    private record TaskAndAccess(ProjectTaskInstanceDO task, TaskAccess access) {}
}
