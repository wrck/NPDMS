package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskAssigneeCandidateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskAssigneeCandidateRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCloseUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCommandQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskByIdQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachineRevisionLockQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.AssignTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.event.TaskAssignedMessage;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_QUERY_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;

/** 项目任务负责人候选和责任区间应用服务。 */
@Service
@RequiredArgsConstructor
public class ProjectTaskAssignmentService {

    private static final Set<String> SERVICE_MANAGER_ROLES = Set.of(
            "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");
    private static final Set<String> TERMINAL_STATUSES = Set.of("DONE", "CLOSED");
    private static final String ASSIGN_SCOPE = "POST:/api/v1/pms/project-tasks/{id}/actions/assign";

    private final ProjectTaskRuntimeMapper taskMapper;
    private final ProjectTaskAssignmentMapper assignmentMapper;
    private final TaskStateMachineMapper stateMachineMapper;
    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectTreeScopeService treeScopeService;
    private final CompanyApi companyApi;
    private final DeptApi deptApi;
    private final OrganizationScopeApi organizationScopeApi;
    private final AdminUserApi adminUserApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;

    public PageResult<ProjectTaskAssigneeCandidateRespVO> getAssigneeCandidates(
            Long taskId, ProjectTaskAssigneeCandidateReqVO request, TaskWorkbenchActor actor) {
        validateCandidateRequest(taskId, request, actor);
        ProjectTaskInstanceDO task = requireTask(taskId, actor);
        ProjectMasterDO project = requireProject(task.getProjectId(), actor);
        requireAssignmentActor(project, actor);
        validateProjectOrganization(project);

        OrganizationUserCandidatePageReqDTO systemRequest = new OrganizationUserCandidatePageReqDTO();
        systemRequest.setCompanyId(project.getCompanyId());
        systemRequest.setDepartmentId(project.getDepartmentId());
        systemRequest.setDepartmentCode(project.getDepartmentCode());
        systemRequest.setKeyword(request.getKeyword());
        systemRequest.setPageNo(request.getPageNo());
        systemRequest.setPageSize(request.getPageSize());
        var page = organizationScopeApi.pageActiveUsers(systemRequest);
        return new PageResult<>(BeanUtils.toBean(page.getList(), ProjectTaskAssigneeCandidateRespVO.class),
                page.getTotal());
    }

    public TaskCommandResult assign(AssignTaskCommand command, TaskWorkbenchActor actor) {
        AtomicReference<Map<String, ?>> auditDetail = new AtomicReference<>(Map.of());
        try {
            validateAssignCommand(command, actor);
            var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                            actor.tenantId(), ASSIGN_SCOPE, actor.actorId(), command.idempotencyKey()),
                    command.requestDigest(), TaskCommandResult.class,
                    () -> assignOnce(command, actor, auditDetail),
                    result -> assignmentFacts(result, actor, auditDetail.get()));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
                throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
            }
            if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                    || execution.response() == null) {
                throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
            }
            TaskCommandResult result = execution.response();
            return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                    ? new TaskCommandResult(result.taskId(), result.taskVersion(), result.taskTreeVersion(),
                    result.status(), "REPLAY_COMPLETED") : result;
        } catch (RuntimeException exception) {
            auditRejected(command, actor, exception);
            throw exception;
        }
    }

    private TaskCommandResult assignOnce(AssignTaskCommand command, TaskWorkbenchActor actor,
                                         AtomicReference<Map<String, ?>> auditDetail) {
        ProjectTaskInstanceDO initial = requireTask(command.taskId(), actor);
        ProjectMasterDO project = taskMapper.selectProjectForCommandForUpdate(
                new ProjectTaskProjectLockQuery(actor.tenantId(), initial.getProjectId()));
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
        ProjectTaskInstanceDO task = taskMapper.selectTaskForAssignmentForUpdate(
                new TaskAssignmentCommandQuery(actor.tenantId(), project.getId(), command.taskId()));
        if (task == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        requireAssignmentActor(project, actor);
        if (!Objects.equals(task.getVersion(), command.expectedTaskVersion())) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        validateProjectOrganization(project);
        adminUserApi.validateUser(command.assigneeUserId());
        if (!organizationScopeApi.hasScope(command.assigneeUserId(), project.getCompanyId(),
                project.getDepartmentId())) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }

        LocalDateTime occurredAt = LocalDateTime.now();
        ProjectTaskAssignmentDO current = assignmentMapper.selectCurrentForUpdate(
                new TaskAssignmentLockQuery(actor.tenantId(), task.getId()));
        if (current == null && !"PENDING_ASSIGN".equals(task.getStatus())) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        if (current != null && !isKnownNonTerminalStatus(task, actor.tenantId())) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        if (current != null && Objects.equals(current.getAssigneeUserId(), command.assigneeUserId())) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        if (current != null && assignmentMapper.closeCurrentIfMatch(new TaskAssignmentCloseUpdate(
                actor.tenantId(), current.getId(), current.getVersion(), occurredAt,
                String.valueOf(actor.actorId()))) != 1) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        ProjectTaskAssignmentDO assignment = new ProjectTaskAssignmentDO();
        assignment.setId(IdWorker.getId());
        assignment.setTenantId(actor.tenantId());
        assignment.setProjectTaskId(task.getId());
        assignment.setAssigneeUserId(command.assigneeUserId());
        assignment.setEffectiveFrom(occurredAt);
        assignment.setAssignedBy(actor.actorId());
        assignment.setReason(command.reason().trim());
        assignment.setVersion(0);
        assignment.setCreator(String.valueOf(actor.actorId()));
        assignment.setUpdater(String.valueOf(actor.actorId()));
        if (assignmentMapper.insertAssignment(assignment) != 1) {
            throw new IllegalStateException("PROJECT_TASK_ASSIGNMENT_WRITE_FAILED");
        }
        String nextStatus = "PENDING_ASSIGN".equals(task.getStatus()) ? "PENDING_START" : task.getStatus();
        if (taskMapper.assignTaskIfMatch(new TaskAssignmentStateUpdate(actor.tenantId(), project.getId(),
                task.getId(), task.getVersion(), task.getStatus(), nextStatus,
                String.valueOf(actor.actorId()))) != 1) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        auditDetail.set(assignmentAuditDetail(task, current, assignment, nextStatus));
        return new TaskCommandResult(task.getId(), task.getVersion() + 1,
                project.getTaskTreeVersion(), nextStatus, "NEW");
    }

    private boolean isKnownNonTerminalStatus(ProjectTaskInstanceDO task, Long tenantId) {
        if (task.getStateMachineRevisionId() == null || task.getStatus() == null
                || TERMINAL_STATUSES.contains(task.getStatus())) {
            return false;
        }
        return stateMachineMapper.selectTransitions(new TaskStateMachineRevisionLockQuery(
                        tenantId, task.getStateMachineRevisionId())).stream()
                .anyMatch(transition -> task.getStatus().equals(transition.getFromStatusCode())
                        || task.getStatus().equals(transition.getToStatusCode()));
    }

    private PlatformCommandExecutionApi.SuccessFacts assignmentFacts(
            TaskCommandResult result, TaskWorkbenchActor actor, Map<String, ?> auditDetail) {
        Long assignmentId = (Long) auditDetail.get("assignmentId");
        Long assigneeUserId = (Long) auditDetail.get("assigneeUserId");
        Long projectId = (Long) auditDetail.get("projectId");
        LocalDateTime occurredAt = (LocalDateTime) auditDetail.get("effectiveFrom");
        TaskAssignedMessage.Payload payload = new TaskAssignedMessage.Payload(actor.tenantId(), projectId,
                result.taskId(), assigneeUserId, assignmentId, result.taskVersion(), actor.actorId(), occurredAt);
        return new PlatformCommandExecutionApi.SuccessFacts("PROJECT_TASK_ASSIGN", "ProjectTask",
                String.valueOf(result.taskId()), actor.correlationId(), JsonUtils.toJsonString(auditDetail),
                "TaskAssigned", JsonUtils.toJsonString(payload));
    }

    private Map<String, ?> assignmentAuditDetail(ProjectTaskInstanceDO task, ProjectTaskAssignmentDO current,
                                                  ProjectTaskAssignmentDO assignment, String nextStatus) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", task.getProjectId());
        detail.put("projectTaskId", task.getId());
        detail.put("previousAssignmentId", current == null ? "NONE" : current.getId());
        detail.put("previousAssigneeUserId", current == null ? "NONE" : current.getAssigneeUserId());
        detail.put("assignmentId", assignment.getId());
        detail.put("assigneeUserId", assignment.getAssigneeUserId());
        detail.put("effectiveFrom", assignment.getEffectiveFrom());
        detail.put("reason", assignment.getReason());
        detail.put("beforeStatus", task.getStatus());
        detail.put("afterStatus", nextStatus);
        return Collections.unmodifiableMap(detail);
    }

    private void validateAssignCommand(AssignTaskCommand command, TaskWorkbenchActor actor) {
        if (command == null || command.taskId() == null || command.taskId() <= 0
                || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || command.assigneeUserId() == null || command.assigneeUserId() <= 0
                || command.reason() == null || command.reason().isBlank() || command.reason().length() > 500
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || !command.requestDigest().matches("[0-9a-f]{64}")
                || actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
    }

    private void auditRejected(AssignTaskCommand command, TaskWorkbenchActor actor, RuntimeException exception) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        if (command != null) {
            if (command.taskId() != null) detail.put("projectTaskId", command.taskId());
            if (command.assigneeUserId() != null) detail.put("assigneeUserId", command.assigneeUserId());
            if (command.expectedTaskVersion() != null) {
                detail.put("expectedTaskVersion", command.expectedTaskVersion());
            }
            if (command.reason() != null && !command.reason().isBlank()) detail.put("reason", command.reason().trim());
        }
        detail.put("failureCode", failureCode(exception));
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "PROJECT_TASK_ASSIGN", "ProjectTask",
                command == null || command.taskId() == null ? "UNKNOWN" : String.valueOf(command.taskId()),
                "REJECTED", Collections.unmodifiableMap(detail));
    }

    private String failureCode(RuntimeException exception) {
        if (exception instanceof ServiceException serviceException) return String.valueOf(serviceException.getCode());
        if (exception instanceof IllegalStateException && exception.getMessage() != null
                && exception.getMessage().startsWith("PROJECT_TASK_")) return exception.getMessage();
        return "PROJECT_TASK_ASSIGN_FAILED";
    }

    private ProjectTaskInstanceDO requireTask(Long taskId, TaskWorkbenchActor actor) {
        ProjectTaskInstanceDO task = taskMapper.selectTask(new TaskByIdQuery(actor.tenantId(), taskId));
        if (task == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        return task;
    }

    private ProjectMasterDO requireProject(Long projectId, TaskWorkbenchActor actor) {
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
        return project;
    }

    private void requireAssignmentActor(ProjectMasterDO project, TaskWorkbenchActor actor) {
        long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        var version = treeVersionMapper.selectLatestActive(rootId);
        if (version == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        treeScopeService.assertFullAccess(new ProjectScopeQuery(actor.tenantId(), actor.actorId(), project.getId(),
                ProjectScopeApi.ACTION_MANAGE, version.getTreeVersion()));
        boolean allowedRole = memberMapper.selectActiveByUser(new ActiveProjectMemberQuery(
                        actor.tenantId(), actor.actorId(), LocalDateTime.now())).stream()
                .anyMatch(item -> (Objects.equals(item.getProjectId(), project.getId())
                        && "PROJECT_MANAGER".equals(item.getMemberRole()))
                        || SERVICE_MANAGER_ROLES.contains(item.getMemberRole()));
        if (!allowedRole) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
    }

    private void validateProjectOrganization(ProjectMasterDO project) {
        if (project.getCompanyId() == null || project.getDepartmentId() == null
                || project.getDepartmentCode() == null || project.getDepartmentCode().isBlank()) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        companyApi.validateCompanyList(Set.of(project.getCompanyId()));
        var department = deptApi.getDept(project.getDepartmentId());
        if (department == null || !Objects.equals(department.getCode(), project.getDepartmentCode())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        deptApi.validateDeptList(Set.of(project.getDepartmentId()));
    }

    private void validateCandidateRequest(Long taskId, ProjectTaskAssigneeCandidateReqVO request,
                                          TaskWorkbenchActor actor) {
        if (taskId == null || taskId <= 0 || request == null || request.getPageNo() == null
                || request.getPageNo() < 1 || request.getPageSize() == null || request.getPageSize() < 1
                || request.getPageSize() > 100 || actor == null || actor.tenantId() == null
                || actor.tenantId() < 0 || actor.actorId() == null || actor.actorId() <= 0) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }
}
