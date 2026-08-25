package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectStageSnapshotSequenceQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentServiceManagerAssignmentsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGovernanceStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectServiceManagerIntervalClose;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.repository.projectgovernance.ProjectStageSnapshotRepository;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.GovernanceActionResult;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.RollbackProjectCommand;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_MANAGE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_ACTION_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_PERSISTENCE_FAILED;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_STATE_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceGuardService.GovernanceAction.ROLLBACK;

@Service
@RequiredArgsConstructor
public class ProjectGovernanceApplicationService {

    public static final String ROLLBACK_SCOPE = "POST:/pms/projects/{id}/actions/rollback";
    public static final String PERMISSION_ROLLBACK = "pms:project:rollback";
    private static final String ACTIVE = "ACTIVE";
    private static final String STAGE_S0 = "S0";
    private static final String UNASSIGNED = "UNASSIGNED";
    private static final Set<String> SERVICE_MANAGER_ROLES =
            Set.of("SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");

    private final PlatformCommandExecutionApi commandExecutionApi;
    private final PermissionApi permissionApi;
    private final ProjectTreeScopeService treeScopeService;
    private final ProjectGovernanceGuardService guardService;
    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectStageSnapshotMapper snapshotMapper;
    private final ProjectStageSnapshotRepository snapshotRepository;

    public GovernanceActionResult rollback(RollbackProjectCommand command,
                                           ProjectGovernanceGuardService.Actor actor) {
        validate(command, actor);
        requirePermission(actor);
        PlatformCommandExecutionApi.ExecutionResult<GovernanceActionResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), ROLLBACK_SCOPE, actor.actorId(), command.idempotencyKey()),
                command.requestDigest(), GovernanceActionResult.class,
                () -> rollbackOnce(command, actor),
                result -> successFacts(command, actor, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        GovernanceActionResult result = execution.response();
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new GovernanceActionResult(result.projectId(), result.action(),
                result.beforeLifecycleStatus(), result.beforeStage(), result.beforeAssignmentStatus(),
                result.lifecycleStatus(),
                result.currentStage(), result.assignmentStatus(), result.projectVersion(),
                result.stageSnapshotId(), result.operationId(), result.operatedAt(), true)
                : result;
    }

    private GovernanceActionResult rollbackOnce(RollbackProjectCommand command,
                                                ProjectGovernanceGuardService.Actor actor) {
        ProjectMasterDO initial = requireProject(command.projectId(), actor.tenantId());
        long treeVersion = requireTreeVersion(initial, actor.tenantId());
        treeScopeService.assertFullAccess(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), command.projectId(), ACTION_MANAGE, treeVersion));

        ProjectMasterDO project = projectMapper.selectByIdForUpdate(command.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        LocalDateTime operatedAt = LocalDateTime.now();
        requirePrimaryServiceManager(project.getId(), actor, operatedAt);
        if (!ACTIVE.equals(project.getLifecycleStatus())) {
            throw exception(PROJECT_GOVERNANCE_STATE_INVALID);
        }
        if (!Objects.equals(project.getVersion(), command.expectedVersion())) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }

        ProjectGovernanceGuardService.VerifiedGuard verified = guardService.verifyAndRevalidate(
                command.guardToken(), command.projectId(), ROLLBACK, command.expectedVersion(), actor);
        if (projectMapper.updateGovernanceStateIfMatch(new ProjectGovernanceStateUpdate(
                actor.tenantId(), project.getId(), command.expectedVersion(), ACTIVE,
                STAGE_S0, ACTIVE, UNASSIGNED, String.valueOf(actor.actorId()))) != 1) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        if (memberMapper.closeEffectiveServiceManagerAssignments(new ProjectServiceManagerIntervalClose(
                actor.tenantId(), project.getId(), operatedAt, String.valueOf(actor.actorId()))) < 1) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }

        String operationId = UUID.randomUUID().toString();
        ProjectStageSnapshotDO snapshot = rollbackSnapshot(command, actor, project, verified,
                operationId, operatedAt);
        if (snapshotRepository.append(snapshot) != 1 || snapshot.getId() == null) {
            throw exception(PROJECT_GOVERNANCE_PERSISTENCE_FAILED);
        }
        return new GovernanceActionResult(project.getId(), ROLLBACK.name(),
                project.getLifecycleStatus(), project.getCurrentStage(), project.getAssignmentStatus(), ACTIVE,
                STAGE_S0, UNASSIGNED, command.expectedVersion() + 1, snapshot.getId(),
                operationId, operatedAt, false);
    }

    private ProjectStageSnapshotDO rollbackSnapshot(
            RollbackProjectCommand command, ProjectGovernanceGuardService.Actor actor,
            ProjectMasterDO project, ProjectGovernanceGuardService.VerifiedGuard verified,
            String operationId, LocalDateTime operatedAt) {
        Integer snapshotNo = snapshotMapper.selectNextSnapshotNo(new ProjectStageSnapshotSequenceQuery(
                actor.tenantId(), project.getId(), STAGE_S0));
        if (snapshotNo == null || snapshotNo <= 0) {
            throw exception(PROJECT_GOVERNANCE_PERSISTENCE_FAILED);
        }
        ProjectStageSnapshotDO snapshot = new ProjectStageSnapshotDO();
        snapshot.setProjectId(project.getId());
        snapshot.setStageCode(STAGE_S0);
        snapshot.setSnapshotNo(snapshotNo);
        snapshot.setOperationType(ROLLBACK.name());
        snapshot.setBeforeStage(project.getCurrentStage());
        snapshot.setAfterStage(STAGE_S0);
        snapshot.setBeforeLifecycleStatus(project.getLifecycleStatus());
        snapshot.setAfterLifecycleStatus(ACTIVE);
        snapshot.setBeforeAssignmentStatus(project.getAssignmentStatus());
        snapshot.setAfterAssignmentStatus(UNASSIGNED);
        snapshot.setReasonCode(command.reasonCode().trim());
        snapshot.setReasonDetail(command.reasonDetail().trim());
        snapshot.setReassignmentRequirement(command.reassignmentRequirement().trim());
        snapshot.setGuardSnapshotJson(JsonUtils.toJsonString(verified.claims()));
        snapshot.setTreeVersion(verified.claims().treeVersion());
        snapshot.setProviderFactsJson(JsonUtils.toJsonString(verified.claims().providerFacts()));
        snapshot.setOperationId(operationId);
        snapshot.setOperatorUserId(actor.actorId());
        snapshot.setOperatedAt(operatedAt);
        snapshot.setCreator(String.valueOf(actor.actorId()));
        snapshot.setUpdater(String.valueOf(actor.actorId()));
        return snapshot;
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            RollbackProjectCommand command, ProjectGovernanceGuardService.Actor actor,
            GovernanceActionResult result) {
        Map<String, Object> beforeState = state(result.beforeLifecycleStatus(),
                result.beforeStage(), result.beforeAssignmentStatus());
        Map<String, Object> afterState = state(
                result.lifecycleStatus(), result.currentStage(), result.assignmentStatus());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", result.projectId());
        detail.put("action", result.action());
        detail.put("stageSnapshotId", result.stageSnapshotId());
        detail.put("operationId", result.operationId());
        detail.put("reasonCode", command.reasonCode().trim());
        detail.put("reasonDetail", command.reasonDetail().trim());
        detail.put("reassignmentRequirement", command.reassignmentRequirement().trim());
        detail.put("projectVersion", result.projectVersion());
        detail.put("expectedVersion", command.expectedVersion());
        detail.put("idempotencyKey", command.idempotencyKey().trim());
        detail.put("requestDigest", command.requestDigest());
        detail.put("operatorUserId", actor.actorId());
        detail.put("occurredAt", result.operatedAt());
        detail.put("traceId", actor.correlationId());
        Map<String, Object> guardSummary = new LinkedHashMap<>();
        guardSummary.put("verified", true);
        guardSummary.put("action", result.action());
        guardSummary.put("projectVersion", command.expectedVersion());
        guardSummary.put("stageSnapshotId", result.stageSnapshotId());
        detail.put("guardResultSummary", guardSummary);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", result.operationId());
        event.put("tenantId", actor.tenantId());
        event.put("projectId", result.projectId());
        event.put("action", result.action());
        event.put("beforeState", beforeState);
        event.put("afterState", afterState);
        event.put("projectVersion", result.projectVersion());
        event.put("stageSnapshotId", result.stageSnapshotId());
        event.put("operatorUserId", actor.actorId());
        event.put("occurredAt", result.operatedAt());
        return new PlatformCommandExecutionApi.SuccessFacts("PROJECT_ROLLBACK", "Project",
                String.valueOf(result.projectId()), actor.correlationId(), JsonUtils.toJsonString(detail),
                "ProjectStageChanged", JsonUtils.toJsonString(event));
    }

    private static Map<String, Object> state(String lifecycle, String stage, String assignment) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("lifecycleStatus", lifecycle);
        state.put("currentStage", stage);
        state.put("assignmentStatus", assignment);
        return state;
    }

    private ProjectMasterDO requireProject(Long projectId, Long tenantId) {
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return project;
    }

    private long requireTreeVersion(ProjectMasterDO project, Long tenantId) {
        Long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        ProjectTreeVersionDO tree = treeVersionMapper.selectLatestActive(rootId);
        if (tree == null || !Objects.equals(tree.getTenantId(), tenantId)
                || tree.getTreeVersion() == null || tree.getTreeVersion() <= 0) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        return tree.getTreeVersion();
    }

    private void requirePrimaryServiceManager(Long projectId,
                                              ProjectGovernanceGuardService.Actor actor,
                                              LocalDateTime effectiveAt) {
        List<ProjectMemberAssignmentDO> assignments = memberMapper.selectCurrentServiceManagerAssignments(
                new CurrentServiceManagerAssignmentsQuery(
                        actor.tenantId(), Set.of(projectId), effectiveAt));
        boolean currentPrimary = assignments.stream().anyMatch(assignment ->
                Objects.equals(assignment.getProjectId(), projectId)
                        && Objects.equals(assignment.getUserId(), actor.actorId())
                        && SERVICE_MANAGER_ROLES.contains(assignment.getMemberRole())
                        && "PRIMARY".equals(assignment.getAssignmentType()));
        if (!currentPrimary) {
            throw exception(PROJECT_GOVERNANCE_ACTION_FORBIDDEN);
        }
    }

    private void requirePermission(ProjectGovernanceGuardService.Actor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_ROLLBACK)) {
            throw exception(PROJECT_GOVERNANCE_ACTION_FORBIDDEN);
        }
    }

    private static void validate(RollbackProjectCommand command,
                                 ProjectGovernanceGuardService.Actor actor) {
        Long trustedTenantId = TenantContextHolder.getRequiredTenantId();
        if (command == null || command.projectId() == null || command.projectId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || blank(command.guardToken()) || blank(command.reasonCode())
                || blank(command.reasonDetail()) || blank(command.reassignmentRequirement())
                || command.reasonCode().trim().length() > 64
                || command.reasonDetail().trim().length() > 1000
                || command.reassignmentRequirement().trim().length() > 1000
                || blank(command.idempotencyKey()) || command.idempotencyKey().length() > 128
                || command.requestDigest() == null || !command.requestDigest().matches("[0-9a-f]{64}")
                || actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.actorId() <= 0 || blank(actor.correlationId())
                || !Objects.equals(actor.tenantId(), trustedTenantId)) {
            throw new IllegalArgumentException("invalid rollback project command");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
