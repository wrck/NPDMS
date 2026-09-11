package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskMaintenanceMapper.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.*;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageProgressionTrigger;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** Z04新入口维护；原指派接口保持，协调负责人不进入执行/审批权限判断。 */
@Service @RequiredArgsConstructor
public class ProjectTaskMaintenanceService {
    public enum Role { RESPONSIBLE, EXECUTOR }
    public record RoleChange(Long taskId, Integer version, Role role, Long userId, String reason, String key) { }
    public record DescriptionChange(Long taskId, Integer version, String html, String key) { }
    public record Candidate(Long userId, String name) { }
    public record RoleEntry(Role role, Long userId, String name, LocalDateTime effectiveFrom, LocalDateTime effectiveTo, String reason) { }
    public record HistoryPage(List<RoleEntry> list, boolean hasMore) { }
    public record View(Long taskId, Integer version, Description description, int descriptionLimit,
                       List<RoleEntry> currentRoles, boolean canAssign, boolean canEdit) { }
    private final ProjectTaskRuntimeMapper tasks;
    private final ProjectTaskMaintenanceMapper maintenance;
    private final ProjectTaskAssignmentMapper assignments;
    private final ProjectMemberAssignmentMapper members;
    private final ProjectTaskAssignmentService assignmentSupport;
    private final ProjectTaskQueryService queries;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    private final PlatformCommandExecutionApi commands;
    private final ProjectStageProgressionTrigger progression;

    @Transactional(readOnly = true)
    public View get(Long taskId, TaskWorkbenchActor actor) {
        require(actor, "pms:project-task:query");
        var workbench = queries.getWorkbench(taskId, actor);
        var current = new ArrayList<RoleEntry>();
        for (Role role : Role.values()) history(taskId, role, 1, 1, actor).list().stream()
                .filter(row -> row.effectiveTo() == null).forEach(current::add);
        return new View(taskId, workbench.getTask().getVersion(), maintenance.selectDescription(new TaskQuery(actor.tenantId(), taskId)),
                TaskRichText.MAX_LENGTH, List.copyOf(current), workbench.getAllowedActions().contains("ASSIGN"),
                workbench.getAllowedActions().contains("UPDATE"));
    }

    public cn.iocoder.yudao.framework.common.pojo.PageResult<Candidate> candidates(Long projectId, String keyword, int pageNo, int pageSize, TaskWorkbenchActor actor) {
        require(actor, "pms:project-task:query");
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw exception(PROJECT_TASK_QUERY_INVALID);
        queries.getWorkspace(projectId, actor);
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        var candidates = activeMembers(projectId, actor).stream()
                .filter(user -> user.getNickname() != null && user.getNickname().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparing(AdminUserRespDTO::getNickname).thenComparing(AdminUserRespDTO::getId))
                .map(user -> new Candidate(user.getId(), user.getNickname())).toList();
        int start = Math.min(candidates.size(), Math.multiplyExact(pageNo - 1, pageSize));
        return new cn.iocoder.yudao.framework.common.pojo.PageResult<>(candidates.subList(start, Math.min(candidates.size(), start + pageSize)), (long) candidates.size());
    }

    public HistoryPage history(Long taskId, Role role, int pageNo, int pageSize, TaskWorkbenchActor actor) {
        require(actor, "pms:project-task:query"); queries.getTask(taskId, actor);
        if (role == null || pageNo < 1 || pageSize < 1 || pageSize > 100) throw exception(PROJECT_TASK_QUERY_INVALID);
        var query = new HistoryQuery(actor.tenantId(), taskId, Math.multiplyExact(pageNo - 1, pageSize), pageSize + 1);
        var rows = role == Role.RESPONSIBLE ? maintenance.selectHistory(query) : maintenance.selectExecutorHistory(query);
        boolean more = rows.size() > pageSize;
        if (more) rows = rows.subList(0, pageSize);
        var names = users.getUserMap(rows.stream().map(TaskResponsibleRow::getUserId).distinct().toList());
        return new HistoryPage(rows.stream().map(row -> new RoleEntry(role, row.getUserId(),
                names.containsKey(row.getUserId()) ? names.get(row.getUserId()).getNickname() : "历史人员",
                row.getEffectiveFrom(), row.getEffectiveTo(), row.getReason())).toList(), more);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public TaskCommandResult changeRole(RoleChange command, TaskWorkbenchActor actor) {
        require(actor, "pms:project-task:assign");
        if (command == null || command.role() == null || command.userId() == null || command.userId() <= 0
                || command.reason() == null || command.reason().isBlank() || command.reason().length() > 500) throw exception(PROJECT_TASK_COMMAND_INVALID);
        var audit = new java.util.concurrent.atomic.AtomicReference<Map<String, ?>>(Map.of());
        return execute(command.taskId(), command.version(), command.key(), "PROJECT_TASK_" + command.role(), command, actor, () -> {
            var locked = lockTask(command.taskId(), command.version(), actor);
            var task = locked.task(); var project = locked.project();
            assignmentSupport.requireAssignmentActor(project, actor);
            if (!"PENDING_ASSIGN".equals(task.getStatus()) && !assignmentSupport.isKnownNonTerminalStatus(task, actor.tenantId())) throw exception(PROJECT_TASK_COMMAND_INVALID);
            if (activeMembers(task.getProjectId(), actor).stream().noneMatch(user -> user.getId().equals(command.userId()))) throw exception(PROJECT_TASK_COMMAND_INVALID);
            var now = LocalDateTime.now();
            if (command.role() == Role.EXECUTOR) {
                var current = assignments.selectCurrentForUpdate(new TaskAssignmentLockQuery(actor.tenantId(), task.getId()));
                if (current == null && !"PENDING_ASSIGN".equals(task.getStatus())
                        || current != null && Objects.equals(current.getAssigneeUserId(), command.userId())) throw exception(PROJECT_TASK_COMMAND_INVALID);
                if (current != null && assignments.closeCurrentIfMatch(new TaskAssignmentCloseUpdate(actor.tenantId(), current.getId(), current.getVersion(), now, String.valueOf(actor.actorId()))) != 1)
                    throw exception(PROJECT_TASK_VERSION_CONFLICT);
                var row = new ProjectTaskAssignmentDO();
                row.setId(IdWorker.getId()); row.setTenantId(actor.tenantId()); row.setProjectTaskId(task.getId());
                row.setAssigneeUserId(command.userId()); row.setEffectiveFrom(now); row.setAssignedBy(actor.actorId()); row.setReason(command.reason().trim());
                row.setVersion(0); row.setCreator(String.valueOf(actor.actorId())); row.setUpdater(String.valueOf(actor.actorId()));
                if (assignments.insertAssignment(row) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
                var status = "PENDING_ASSIGN".equals(task.getStatus()) ? "PENDING_START" : task.getStatus();
                if (tasks.assignTaskIfMatch(new TaskAssignmentStateUpdate(actor.tenantId(), task.getProjectId(), task.getId(), task.getVersion(), task.getStatus(), status, String.valueOf(actor.actorId()))) != 1)
                    throw exception(PROJECT_TASK_VERSION_CONFLICT);
                audit.set(assignmentSupport.assignmentAuditDetail(task, current, row, status));
                return new TaskCommandResult(task.getId(), task.getVersion() + 1, project.getTaskTreeVersion(), status, "NEW");
            }
            var current = maintenance.selectCurrentForUpdate(new TaskQuery(actor.tenantId(), task.getId()));
            if (current != null && Objects.equals(current.getUserId(), command.userId())) throw exception(PROJECT_TASK_COMMAND_INVALID);
            if (current != null && maintenance.closeResponsible(new CloseResponsible(actor.tenantId(), current.getId(), current.getVersion(), now, String.valueOf(actor.actorId()))) != 1)
                throw exception(PROJECT_TASK_VERSION_CONFLICT);
            var row = new TaskResponsibleRow(); row.setId(IdWorker.getId()); row.setTenantId(actor.tenantId()); row.setProjectId(task.getProjectId());
            row.setProjectTaskId(task.getId()); row.setUserId(command.userId()); row.setEffectiveFrom(now); row.setAssignedBy(actor.actorId()); row.setReason(command.reason().trim());
            if (maintenance.insertResponsible(row) != 1 || tasks.incrementTaskVersionIfMatch(new TaskVersionUpdate(actor.tenantId(), task.getId(), task.getVersion(), String.valueOf(actor.actorId()))) != 1)
                throw exception(PROJECT_TASK_VERSION_CONFLICT);
            audit.set(Map.of("projectId",task.getProjectId(), "userId",command.userId(), "role",command.role(), "effectiveFrom",now, "reason",command.reason()));
            return new TaskCommandResult(task.getId(), task.getVersion() + 1, project.getTaskTreeVersion(), task.getStatus(), "NEW");
        }, result -> command.role() == Role.EXECUTOR ? assignmentSupport.assignmentFacts(result, actor, audit.get())
                : new PlatformCommandExecutionApi.SuccessFacts("PROJECT_TASK_RESPONSIBLE_ASSIGN", "ProjectTask", String.valueOf(command.taskId()), actor.correlationId(), JsonUtils.toJsonString(audit.get()), null, null));
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskCommandResult saveDescription(DescriptionChange command, TaskWorkbenchActor actor) {
        require(actor, "pms:project-task:update");
        if (command == null || command.html() == null) throw exception(PROJECT_TASK_COMMAND_INVALID);
        var audit = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
        return execute(command.taskId(), command.version(), command.key(), "PROJECT_TASK_DESCRIPTION", command, actor, () -> {
            var locked = lockTask(command.taskId(), command.version(), actor); var task = locked.task();
            if (!queries.getWorkbench(task.getId(), actor).getAllowedActions().contains("UPDATE")) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            String clean = TaskRichText.clean(command.html());
            var facts = new LinkedHashMap<String, Object>();
            facts.put("before", maintenance.selectDescription(new TaskQuery(actor.tenantId(), task.getId())));
            facts.put("after", new Description(clean, TaskRichText.HTML)); facts.put("taskId", task.getId());
            facts.put("beforeVersion", task.getVersion()); audit.set(facts);
            if (maintenance.updateDescription(new DescriptionUpdate(actor.tenantId(), task.getId(), task.getVersion(), String.valueOf(actor.actorId()), clean, TaskRichText.HTML)) != 1)
                throw exception(PROJECT_TASK_VERSION_CONFLICT);
            return new TaskCommandResult(task.getId(), task.getVersion() + 1, locked.project().getTaskTreeVersion(), task.getStatus(), "NEW");
        }, result -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_TASK_DESCRIPTION", "ProjectTask", String.valueOf(command.taskId()), actor.correlationId(), JsonUtils.toJsonString(audit.get()), null, null));
    }

    private TaskCommandResult execute(Long taskId, Integer version, String key, String operation, Object payload, TaskWorkbenchActor actor,
                                      Supplier<TaskCommandResult> action, java.util.function.Function<TaskCommandResult, PlatformCommandExecutionApi.SuccessFacts> facts) {
        if (taskId == null || taskId <= 0 || version == null || version < 0 || key == null || key.isBlank() || key.length() > 128) throw exception(PROJECT_TASK_COMMAND_INVALID);
        var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(), operation, actor.actorId(), key),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(payload)), TaskCommandResult.class, action, facts);
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        if (result.decision() == PlatformCommandExecutionApi.Decision.NEW) {
            var task = tasks.selectTask(new TaskByIdQuery(actor.tenantId(), taskId));
            progression.afterChange(task.getProjectId());
        }
        return result.response();
    }
    private record LockedTask(ProjectTaskInstanceDO task, cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO project) { }
    private LockedTask lockTask(Long taskId, Integer version, TaskWorkbenchActor actor) {
        var initial = tasks.selectTask(new TaskByIdQuery(actor.tenantId(), taskId));
        if (initial == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var project = tasks.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(actor.tenantId(), initial.getProjectId()));
        if (project == null || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var task = tasks.selectTaskForAssignmentForUpdate(new TaskAssignmentCommandQuery(actor.tenantId(), project.getId(), taskId));
        if (task == null || !Objects.equals(task.getVersion(), version)) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        return new LockedTask(task, project);
    }
    private List<AdminUserRespDTO> activeMembers(Long projectId, TaskWorkbenchActor actor) {
        var ids = members.selectActiveForAssignmentState(new ProjectAssignmentStateQuery(projectId, LocalDateTime.now())).stream()
                .filter(row -> Objects.equals(row.getTenantId(), actor.tenantId())).map(row -> row.getUserId()).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? List.of() : users.getUserList(ids).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())).toList();
    }
    private void require(TaskWorkbenchActor actor, String permission) {
        if (actor == null || actor.actorId() == null || actor.actorId() <= 0 || actor.tenantId() == null || !Objects.equals(actor.tenantId(), TenantContextHolder.getTenantId())
                || !permissions.hasAnyPermissions(actor.actorId(), permission)) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
    }
}
