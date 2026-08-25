package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskDependencyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMilestoneInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskDependencyMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.NewTaskTreePathInsert;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskBasicUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskMoveLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskStructureUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeVersionUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskByIdQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskDependencyPathQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishedQuery;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.AddDependencyCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.CreateTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.MoveTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.UpdateTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectTaskCommandService {

    private static final Set<String> DEPENDENCY_TYPES = Set.of(
            "FINISH_TO_START", "START_TO_START", "FINISH_TO_FINISH", "START_TO_FINISH");
    private static final Set<String> TERMINAL_STATUSES = Set.of("DONE", "CLOSED");

    private final ProjectTaskRuntimeMapper taskMapper;
    private final ProjectTaskInstanceMapper taskInstanceMapper;
    private final ProjectTaskDependencyMapper dependencyMapper;
    private final ProjectTaskExecutionContractMapper contractMapper;
    private final TaskExecutionContractFactory contractFactory;
    private final TaskStateMachineMapper stateMachineMapper;
    private final ProjectStageInstanceMapper stageMapper;
    private final ProjectMilestoneInstanceMapper milestoneMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTreeVersionMapper projectTreeVersionMapper;
    private final ProjectTreeScopeService treeScopeService;
    private final PlatformCommandExecutionApi commandExecutionApi;

    public TaskCommandResult create(CreateTaskCommand command, TaskWorkbenchActor actor) {
        validateCommon(command == null ? null : command.idempotencyKey(),
                command == null ? null : command.requestDigest(), actor);
        return execute("POST:/api/v1/pms/projects/{id}/tasks", command.idempotencyKey(),
                command.requestDigest(), actor, () -> createOnce(command, actor), "PROJECT_TASK_CREATE");
    }

    public TaskCommandResult update(UpdateTaskCommand command, TaskWorkbenchActor actor) {
        validateActor(actor);
        return updateOnce(command, actor);
    }

    public TaskCommandResult move(MoveTaskCommand command, TaskWorkbenchActor actor) {
        validateCommon(command == null ? null : command.idempotencyKey(),
                command == null ? null : command.requestDigest(), actor);
        return execute("POST:/api/v1/pms/project-tasks/{id}/actions/move", command.idempotencyKey(),
                command.requestDigest(), actor, () -> moveOnce(command, actor), "PROJECT_TASK_MOVE");
    }

    public TaskCommandResult addDependency(AddDependencyCommand command, TaskWorkbenchActor actor) {
        validateCommon(command == null ? null : command.idempotencyKey(),
                command == null ? null : command.requestDigest(), actor);
        return execute("POST:/api/v1/pms/project-tasks/{id}/dependencies", command.idempotencyKey(),
                command.requestDigest(), actor, () -> addDependencyOnce(command, actor), "PROJECT_TASK_DEPENDENCY_ADD");
    }

    private TaskCommandResult createOnce(CreateTaskCommand command, TaskWorkbenchActor actor) {
        if (command.projectId() == null || blank(command.taskCode()) || blank(command.name())
                || blank(command.stageCode()) || invalidPlan(command.planStartTime(), command.planEndTime())) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        ProjectMasterDO project = lockProject(actor.tenantId(), command.projectId());
        requireActiveProjectManager(project, actor);
        if (stageMapper.selectByProjectIdAndStageCode(project.getId(), command.stageCode().trim()) == null) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        ProjectTaskInstanceDO parent = command.parentTaskId() == null ? null
                : taskMapper.selectTask(new TaskByIdQuery(actor.tenantId(), command.parentTaskId()));
        if (parent != null && !Objects.equals(parent.getProjectId(), project.getId())
                || command.parentTaskId() != null && parent == null) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        if (taskInstanceMapper.selectByProjectIdAndTaskCode(project.getId(), command.taskCode().trim()) != null) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        var stateMachine = stateMachineMapper.selectCurrentPublished(TaskStateMachinePublishedQuery.builder()
                .tenantId(actor.tenantId()).effectiveAt(LocalDateTime.now()).build());
        if (stateMachine == null) throw exception(PROJECT_TASK_COMMAND_INVALID);
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
        task.setId(IdWorker.getId());
        task.setTenantId(actor.tenantId());
        task.setProjectId(project.getId());
        task.setTaskCode(command.taskCode().trim());
        task.setName(command.name().trim());
        task.setStageCode(command.stageCode().trim());
        task.setParentTaskId(parent == null ? null : parent.getId());
        task.setParentTaskCode(null);
        task.setRootTaskId(parent == null ? task.getId() : parent.getRootTaskId());
        task.setTreeDepth(parent == null ? 0 : parent.getTreeDepth() + 1);
        task.setBusinessLevelCode(trim(command.businessLevelCode()));
        task.setPlanStartTime(command.planStartTime());
        task.setPlanEndTime(command.planEndTime());
        task.setPriority(command.priority());
        task.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        task.setDescription(trim(command.description()));
        task.setProgress(BigDecimal.ZERO);
        task.setStateMachineRevisionId(stateMachine.getId());
        task.setStatus("PENDING_ASSIGN");
        task.setVersion(0);
        if (taskMapper.insert(task) != 1 || taskMapper.insertNewTaskPaths(new NewTaskTreePathInsert(
                actor.tenantId(), project.getId(), task.getId(), task.getParentTaskId(), actorName(actor))) < 1) {
            throw new IllegalStateException("PROJECT_TASK_CREATE_WRITE_FAILED");
        }
        ProjectTaskExecutionContractDO contract = contractFactory.createTaskNative(task.getId(), LocalDateTime.now());
        contract.setTenantId(actor.tenantId());
        if (contractMapper.insert(contract) != 1) throw new IllegalStateException("PROJECT_TASK_CONTRACT_WRITE_FAILED");
        long nextTreeVersion = project.getTaskTreeVersion() + 1;
        requireTreeVersionIncrement(project, actor);
        return new TaskCommandResult(task.getId(), 0, nextTreeVersion, task.getStatus(), "NEW");
    }

    private TaskCommandResult updateOnce(UpdateTaskCommand command, TaskWorkbenchActor actor) {
        if (command.taskId() == null || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || blank(command.name()) || invalidPlan(command.planStartTime(), command.planEndTime())) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        ProjectTaskInstanceDO task = requireTask(command.taskId(), actor);
        ProjectMasterDO project = lockProject(actor.tenantId(), task.getProjectId());
        requireActiveProjectManager(project, actor);
        if (TERMINAL_STATUSES.contains(task.getStatus())) throw exception(PROJECT_TASK_COMMAND_INVALID);
        int changed = taskMapper.updateBasicIfMatch(new ProjectTaskBasicUpdate(actor.tenantId(), task.getId(),
                command.expectedTaskVersion(), command.name().trim(), trim(command.businessLevelCode()),
                command.planStartTime(), command.planEndTime(), command.priority(), command.sortOrder(),
                trim(command.description()), actorName(actor)));
        if (changed != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        return new TaskCommandResult(task.getId(), command.expectedTaskVersion() + 1,
                project.getTaskTreeVersion(), task.getStatus(), "NEW");
    }

    private TaskCommandResult moveOnce(MoveTaskCommand command, TaskWorkbenchActor actor) {
        if (command.taskId() == null || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || command.expectedTaskTreeVersion() == null || command.expectedTaskTreeVersion() < 0
                || blank(command.reason())) throw exception(PROJECT_TASK_COMMAND_INVALID);
        ProjectTaskInstanceDO initial = requireTask(command.taskId(), actor);
        ProjectTaskRuntimeMapper.ProjectTaskMoveLocks locks = taskMapper.selectMoveLocks(
                new ProjectTaskMoveLockQuery(actor.tenantId(), initial.getProjectId(), command.taskId(),
                        command.targetParentTaskId()));
        ProjectMasterDO project = locks.project();
        ProjectTaskInstanceDO source = locks.sourceTask();
        ProjectTaskInstanceDO target = locks.targetParentTask();
        requireActiveProjectManager(project, actor);
        if (source == null || !Objects.equals(source.getVersion(), command.expectedTaskVersion())
                || !Objects.equals(project.getTaskTreeVersion(), command.expectedTaskTreeVersion())) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        if (command.targetParentTaskId() != null && target == null || locks.targetInsideMovedSubtree()
                || TERMINAL_STATUSES.contains(source.getStatus())
                || target != null && TERMINAL_STATUSES.contains(target.getStatus())
                || moveBlockedByFrozenFact(source)) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        long newRoot = target == null ? source.getId() : target.getRootTaskId();
        int newDepth = target == null ? 0 : target.getTreeDepth() + 1;
        ProjectTaskStructureUpdate update = new ProjectTaskStructureUpdate(actor.tenantId(), source.getProjectId(),
                source.getId(), target == null ? null : target.getId(), command.expectedTaskVersion(), newRoot,
                newDepth, newDepth - source.getTreeDepth(), actorName(actor));
        if (taskMapper.updateStructureIfMatch(update) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        taskMapper.rebuildMovedSubtreePaths(update);
        requireTreeVersionIncrement(project, actor);
        return new TaskCommandResult(source.getId(), source.getVersion() + 1,
                project.getTaskTreeVersion() + 1, source.getStatus(), "NEW");
    }

    private TaskCommandResult addDependencyOnce(AddDependencyCommand command, TaskWorkbenchActor actor) {
        if (command.taskId() == null || command.predecessorTaskId() == null
                || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || !DEPENDENCY_TYPES.contains(command.dependencyTypeCode())
                || command.taskId().equals(command.predecessorTaskId())) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        ProjectTaskInstanceDO successor = requireTask(command.taskId(), actor);
        ProjectMasterDO project = lockProject(actor.tenantId(), successor.getProjectId());
        requireActiveProjectManager(project, actor);
        ProjectTaskInstanceDO predecessor = taskMapper.selectTask(
                new TaskByIdQuery(actor.tenantId(), command.predecessorTaskId()));
        if (predecessor == null || !Objects.equals(predecessor.getProjectId(), successor.getProjectId())
                || TERMINAL_STATUSES.contains(successor.getStatus())
                || dependencyMapper.existsDependencyPath(new TaskDependencyPathQuery(actor.tenantId(),
                successor.getProjectId(), successor.getId(), predecessor.getId()))) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        ProjectTaskDependencyDO dependency = new ProjectTaskDependencyDO();
        dependency.setTenantId(actor.tenantId());
        dependency.setProjectId(successor.getProjectId());
        dependency.setPredecessorTaskId(predecessor.getId());
        dependency.setSuccessorTaskId(successor.getId());
        dependency.setDependencyTypeCode(command.dependencyTypeCode());
        dependency.setVersion(0);
        if (dependencyMapper.insert(dependency) != 1 || taskMapper.incrementTaskVersionIfMatch(
                actor.tenantId(), successor.getId(), command.expectedTaskVersion(), actorName(actor)) != 1) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        return new TaskCommandResult(successor.getId(), command.expectedTaskVersion() + 1,
                project.getTaskTreeVersion(), successor.getStatus(), "NEW");
    }

    private boolean moveBlockedByFrozenFact(ProjectTaskInstanceDO source) {
        if (source.getMilestoneId() != null) {
            var milestone = milestoneMapper.selectById(source.getMilestoneId());
            if (milestone != null && "ACHIEVED".equals(milestone.getStatus())) return true;
        }
        ProjectTaskExecutionContractDO contract = contractMapper.selectCurrentByTaskId(source.getId());
        return contract != null && "APPROVAL".equals(contract.getWorkBindingTypeCode());
    }

    private ProjectMasterDO lockProject(Long tenantId, Long projectId) {
        ProjectMasterDO project = taskMapper.selectProjectForCommandForUpdate(
                new ProjectTaskProjectLockQuery(tenantId, projectId));
        if (project == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        return project;
    }

    private ProjectTaskInstanceDO requireTask(Long taskId, TaskWorkbenchActor actor) {
        ProjectTaskInstanceDO task = taskMapper.selectTask(new TaskByIdQuery(actor.tenantId(), taskId));
        if (task == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        return task;
    }

    private void requireActiveProjectManager(ProjectMasterDO project, TaskWorkbenchActor actor) {
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        var version = projectTreeVersionMapper.selectLatestActive(rootId);
        if (version == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        treeScopeService.assertFullAccess(new ProjectScopeQuery(actor.tenantId(), actor.actorId(), project.getId(),
                ProjectScopeApi.ACTION_MANAGE, version.getTreeVersion()));
        boolean manager = memberMapper.selectActiveByUser(new ActiveProjectMemberQuery(
                        actor.tenantId(), actor.actorId(), LocalDateTime.now())).stream()
                .anyMatch(item -> Objects.equals(item.getProjectId(), project.getId())
                        && "PROJECT_MANAGER".equals(item.getMemberRole()));
        if (!manager) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
    }

    private void requireTreeVersionIncrement(ProjectMasterDO project, TaskWorkbenchActor actor) {
        if (taskMapper.incrementTaskTreeVersion(new ProjectTaskTreeVersionUpdate(actor.tenantId(), project.getId(),
                project.getTaskTreeVersion(), actorName(actor))) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
    }

    private TaskCommandResult execute(String scope, String key, String digest, TaskWorkbenchActor actor,
                                      java.util.function.Supplier<TaskCommandResult> action, String operation) {
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), scope, actor.actorId(), key), digest, TaskCommandResult.class, action,
                result -> facts(operation, result, actor));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT)
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null)
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        TaskCommandResult result = execution.response();
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new TaskCommandResult(result.taskId(), result.taskVersion(), result.taskTreeVersion(),
                result.status(), "REPLAY_COMPLETED") : result;
    }

    private PlatformCommandExecutionApi.SuccessFacts facts(String operation, TaskCommandResult result,
                                                            TaskWorkbenchActor actor) {
        String detail = JsonUtils.toJsonString(Map.of("taskId", result.taskId(), "taskVersion",
                result.taskVersion(), "taskTreeVersion", result.taskTreeVersion(), "status", result.status()));
        return new PlatformCommandExecutionApi.SuccessFacts(operation, "ProjectTask",
                String.valueOf(result.taskId()), actor.correlationId(), detail, null, null);
    }

    private void validateCommon(String key, String digest, TaskWorkbenchActor actor) {
        if (blank(key) || digest == null || !digest.matches("[0-9a-f]{64}"))
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        validateActor(actor);
    }

    private void validateActor(TaskWorkbenchActor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0 || actor.actorId() == null
                || actor.actorId() <= 0 || blank(actor.correlationId())) throw exception(PROJECT_TASK_COMMAND_INVALID);
    }

    private boolean invalidPlan(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null && end.isBefore(start);
    }

    private String actorName(TaskWorkbenchActor actor) { return String.valueOf(actor.actorId()); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String trim(String value) { return blank(value) ? null : value.trim(); }
}
