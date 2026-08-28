package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressFactDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ApplicableLeafTaskProgress;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ApplicableLeafTaskProgressQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProgressVersionUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCommandQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskByIdQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskProgressUpdate;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.ProjectProgressFact;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.UpdateTaskProgressCommand;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectTaskProgressService {

    private static final String EXECUTE_PERMISSION = "pms:project-task:execute";
    private static final BigDecimal DEFAULT_WEIGHT = BigDecimal.ONE;

    private final ProjectTaskRuntimeMapper taskMapper;
    private final ProjectTaskAssignmentMapper assignmentMapper;
    private final ProjectProgressFactMapper factMapper;
    private final ProjectTreeVersionMapper projectTreeVersionMapper;
    private final ProjectTreeScopeService treeScopeService;
    private final PermissionApi permissionApi;
    private final OperationAuditApi operationAuditApi;

    @Transactional(rollbackFor = Exception.class)
    public TaskCommandResult updateProgress(UpdateTaskProgressCommand command, TaskWorkbenchActor actor) {
        validate(command, actor);
        ProjectTaskInstanceDO initial = taskMapper.selectTask(new TaskByIdQuery(actor.tenantId(), command.taskId()));
        if (initial == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        ProjectMasterDO project = taskMapper.selectProjectForCommandForUpdate(
                new ProjectTaskProjectLockQuery(actor.tenantId(), initial.getProjectId()));
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
        ProjectTaskInstanceDO task = taskMapper.selectTaskForAssignmentForUpdate(
                new TaskAssignmentCommandQuery(actor.tenantId(), project.getId(), command.taskId()));
        if (task == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        if (!Objects.equals(task.getVersion(), command.expectedTaskVersion())) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        requireExecuteAccess(project, task, actor);
        if (!"IN_PROGRESS".equals(task.getStatus())) throw exception(PROJECT_TASK_COMMAND_INVALID);
        List<ApplicableLeafTaskProgress> leaves = taskMapper.selectApplicableLeaves(
                new ApplicableLeafTaskProgressQuery(actor.tenantId(), project.getId()));
        if (leaves.stream().noneMatch(leaf -> Objects.equals(leaf.getTaskId(), task.getId()))) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        LocalDateTime occurredAt = LocalDateTime.now();
        if (taskMapper.updateProgressIfMatch(new TaskProgressUpdate(actor.tenantId(), project.getId(), task.getId(),
                command.expectedTaskVersion(), command.progress(), occurredAt, String.valueOf(actor.actorId()))) != 1) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        ProjectProgressFact fact = recomputeLocked(project, actor.tenantId(), project.getTaskProgressVersion(),
                occurredAt, String.valueOf(actor.actorId())).orElseThrow(
                () -> exception(PROJECT_TASK_COMMAND_INVALID));
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("projectId", project.getId());
        audit.put("projectTaskId", task.getId());
        audit.put("beforeProgress", task.getProgress());
        audit.put("afterProgress", command.progress());
        audit.put("taskVersion", command.expectedTaskVersion() + 1);
        audit.put("taskProgressVersion", fact.factVersion());
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "PROJECT_TASK_PROGRESS_UPDATE", "ProjectTask", String.valueOf(task.getId()),
                "SUCCESS", Map.copyOf(audit));
        return new TaskCommandResult(task.getId(), command.expectedTaskVersion() + 1,
                project.getTaskTreeVersion(), task.getStatus(), "NEW");
    }

    @Transactional(rollbackFor = Exception.class)
    public Optional<ProjectProgressFact> recompute(Long tenantId, Long projectId,
                                                    long expectedTaskProgressVersion,
                                                    LocalDateTime occurredAt) {
        ProjectMasterDO project = taskMapper.selectProjectForCommandForUpdate(
                new ProjectTaskProjectLockQuery(tenantId, projectId));
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
        return recomputeLocked(project, tenantId, expectedTaskProgressVersion, occurredAt, "0");
    }

    private Optional<ProjectProgressFact> recomputeLocked(ProjectMasterDO project, Long tenantId,
                                                           long expectedTaskProgressVersion,
                                                           LocalDateTime occurredAt, String updater) {
        if (!Objects.equals(project.getTaskProgressVersion(), expectedTaskProgressVersion)) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        List<ApplicableLeafTaskProgress> leaves = taskMapper.selectApplicableLeaves(
                new ApplicableLeafTaskProgressQuery(tenantId, project.getId()));
        if (leaves.isEmpty()) return Optional.empty();
        BigDecimal progress = aggregate(leaves);
        long nextVersion = expectedTaskProgressVersion + 1;
        if (taskMapper.incrementTaskProgressVersion(new ProjectTaskProgressVersionUpdate(tenantId, project.getId(),
                expectedTaskProgressVersion, occurredAt, updater)) != 1) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        String watermark = JsonUtils.toJsonString(Map.of(
                "taskTreeVersion", project.getTaskTreeVersion(),
                "taskProgressVersion", nextVersion,
                "participantCount", leaves.size()));
        ProjectProgressFactDO fact = new ProjectProgressFactDO();
        fact.setId(IdWorker.getId());
        fact.setTenantId(tenantId);
        fact.setProjectId(project.getId());
        fact.setFactSourceType("PROJECT_TASK");
        fact.setFactSourceId(String.valueOf(project.getId()));
        fact.setFactVersion(nextVersion);
        fact.setProgress(progress);
        fact.setSourceWatermark(watermark);
        fact.setOccurredAt(occurredAt);
        fact.setVersion(0);
        fact.setCreator(updater);
        fact.setUpdater(updater);
        if (factMapper.insert(fact) != 1) throw new IllegalStateException("PROJECT_TASK_PROGRESS_FACT_WRITE_FAILED");
        return Optional.of(new ProjectProgressFact(project.getId(), nextVersion, progress, watermark));
    }

    private BigDecimal aggregate(List<ApplicableLeafTaskProgress> leaves) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ApplicableLeafTaskProgress leaf : leaves) {
            BigDecimal progress = effectiveProgress(leaf);
            BigDecimal weight = leaf.getEstimatedHours() != null
                    && leaf.getEstimatedHours().compareTo(BigDecimal.ZERO) > 0
                    ? leaf.getEstimatedHours() : DEFAULT_WEIGHT;
            weighted = weighted.add(progress.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }
        return weighted.divide(totalWeight, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal effectiveProgress(ApplicableLeafTaskProgress leaf) {
        if (leaf.getStatus() == null || leaf.getProgress() == null
                || leaf.getProgress().compareTo(BigDecimal.ZERO) < 0
                || leaf.getProgress().compareTo(new BigDecimal("100")) > 0) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        return switch (leaf.getStatus()) {
            case "PENDING_ASSIGN", "PENDING_START" -> BigDecimal.ZERO;
            case "PENDING_ACCEPT" -> new BigDecimal("99");
            case "DONE" -> new BigDecimal("100");
            case "IN_PROGRESS", "CLOSED" -> leaf.getProgress();
            default -> throw exception(PROJECT_TASK_COMMAND_INVALID);
        };
    }

    private void requireExecuteAccess(ProjectMasterDO project, ProjectTaskInstanceDO task,
                                      TaskWorkbenchActor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), EXECUTE_PERMISSION)) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
        long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        var treeVersion = projectTreeVersionMapper.selectLatestActive(rootId);
        if (treeVersion == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        treeScopeService.assertFullAccess(new ProjectScopeQuery(actor.tenantId(), actor.actorId(), project.getId(),
                ProjectScopeApi.ACTION_EDIT, treeVersion.getTreeVersion()));
        var assignment = assignmentMapper.selectCurrentForUpdate(
                new TaskAssignmentLockQuery(actor.tenantId(), task.getId()));
        if (assignment == null || !Objects.equals(assignment.getAssigneeUserId(), actor.actorId())) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
    }

    private void validate(UpdateTaskProgressCommand command, TaskWorkbenchActor actor) {
        if (command == null || command.taskId() == null || command.taskId() <= 0
                || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || command.progress() == null || command.progress() < 0 || command.progress() > 99
                || actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
    }
}
