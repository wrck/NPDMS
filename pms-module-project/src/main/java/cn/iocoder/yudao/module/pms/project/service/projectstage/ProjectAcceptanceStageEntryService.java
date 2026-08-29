package cn.iocoder.yudao.module.pms.project.service.projectstage;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeBindingApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingResult;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceStageEntryBindingCommand;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectStageSnapshotSequenceQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGovernanceStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageTransitionQuery;
import cn.iocoder.yudao.module.pms.project.dal.repository.projectgovernance.ProjectStageSnapshotRepository;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_MANAGE;
import static cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules.STATUS_S5;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_ACTION_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_PERSISTENCE_FAILED;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_STATE_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PHASE_SEQUENCE_INVALID;

@Service
@RequiredArgsConstructor
public class ProjectAcceptanceStageEntryService {

    public static final String IDEMPOTENCY_SCOPE = "POST:/pms/projects/{id}/actions/enter-acceptance-stage";
    public static final String PERMISSION_UPDATE = "pms:project:update";
    private static final String ACTIVE = "ACTIVE";
    private static final String DONE = "DONE";
    private static final String PENDING = "PENDING";
    private static final String STAGE_ACTIVE = "ACTIVE";
    private static final String STAGE_ENTRY = "STAGE_ENTRY";

    private final PlatformCommandExecutionApi commandExecutionApi;
    private final PermissionApi permissionApi;
    private final ProjectTreeScopeService treeScopeService;
    private final ProjectMasterMapper projectMapper;
    private final ProjectStageInstanceMapper stageMapper;
    private final ProjectStageSnapshotMapper snapshotMapper;
    private final ProjectStageSnapshotRepository snapshotRepository;
    private final AcceptanceScopeBindingApi bindingApi;

    public ProjectAcceptanceStageEntryResult enter(ProjectAcceptanceStageEntryCommand command, Actor actor) {
        validate(command, actor);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_UPDATE)) {
            throw exception(PROJECT_GOVERNANCE_ACTION_FORBIDDEN);
        }
        PlatformCommandExecutionApi.ExecutionResult<ProjectAcceptanceStageEntryResult> execution =
                commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                                actor.tenantId(), IDEMPOTENCY_SCOPE, actor.actorId(), command.idempotencyKey()),
                        command.requestDigest(), ProjectAcceptanceStageEntryResult.class,
                        () -> enterOnce(command, actor), result -> successFacts(command, actor, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        ProjectAcceptanceStageEntryResult result = execution.response();
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new ProjectAcceptanceStageEntryResult(result.projectId(), result.beforeStageCode(),
                result.acceptanceStageCode(), result.projectVersion(), result.projectStageSnapshotId(),
                result.bindingCount(), result.operationId(), result.operatedAt(), true) : result;
    }

    private ProjectAcceptanceStageEntryResult enterOnce(ProjectAcceptanceStageEntryCommand command, Actor actor) {
        var scope = treeScopeService.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(),
                actor.actorId(), command.projectId(), ACTION_MANAGE, command.expectedTreeVersion()));
        if (scope == null || !Objects.equals(scope.treeVersion(), command.expectedTreeVersion())
                || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(command.projectId())) {
            throw exception(PROJECT_GOVERNANCE_ACTION_FORBIDDEN);
        }
        ProjectMasterDO project = projectMapper.selectByIdForUpdate(command.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        if (!Objects.equals(project.getVersion(), command.expectedProjectVersion())) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        if (!ACTIVE.equals(project.getLifecycleStatus()) || blank(project.getCurrentStage())) {
            throw exception(PROJECT_GOVERNANCE_STATE_INVALID);
        }
        List<ProjectStageInstanceDO> stages = stageMapper.selectListForTransition(
                new ProjectStageTransitionQuery(actor.tenantId(), project.getId()));
        StageTransition transition = requireTransition(project, stages, actor.tenantId());
        LocalDateTime now = LocalDateTime.now();
        String operationId = UUID.randomUUID().toString();
        ProjectStageSnapshotDO snapshot = appendSnapshot(command, actor, project, transition, operationId, now);
        AcceptanceScopeBindingResult bindings = bindingApi.bindForStageEntry(new AcceptanceStageEntryBindingCommand(
                actor.tenantId(), project.getId(), project.getVersion(), snapshot.getId(),
                transition.current().getStageCode(), transition.target().getStageCode(), operationId));
        int bindingCount = validateBindings(bindings, snapshot.getId());
        if (stageMapper.updateStatusIfMatch(new ProjectStageStatusUpdate(actor.tenantId(), project.getId(),
                transition.target().getId(), transition.target().getVersion(), PENDING, STAGE_ACTIVE,
                String.valueOf(actor.actorId()))) != 1) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        if (projectMapper.updateGovernanceStateIfMatch(new ProjectGovernanceStateUpdate(actor.tenantId(),
                project.getId(), project.getVersion(), ACTIVE, transition.target().getStageCode(), ACTIVE,
                project.getAssignmentStatus(), String.valueOf(actor.actorId()))) != 1) {
            throw exception(PROJECT_GOVERNANCE_VERSION_CONFLICT);
        }
        return new ProjectAcceptanceStageEntryResult(project.getId(), transition.current().getStageCode(),
                transition.target().getStageCode(), project.getVersion() + 1, snapshot.getId(), bindingCount,
                operationId, now, false);
    }

    private StageTransition requireTransition(ProjectMasterDO project, List<ProjectStageInstanceDO> stages,
                                              Long tenantId) {
        if (stages == null || stages.isEmpty()) {
            throw exception(PROJECT_PHASE_SEQUENCE_INVALID);
        }
        for (int index = 0; index < stages.size(); index++) {
            ProjectStageInstanceDO current = stages.get(index);
            if (!Objects.equals(current.getTenantId(), tenantId)
                    || !Objects.equals(current.getProjectId(), project.getId())) {
                throw exception(PROJECT_PHASE_SEQUENCE_INVALID);
            }
            if (Objects.equals(current.getStageCode(), project.getCurrentStage())) {
                if (index + 1 >= stages.size()) {
                    throw exception(PROJECT_PHASE_SEQUENCE_INVALID);
                }
                ProjectStageInstanceDO target = stages.get(index + 1);
                if (!DONE.equals(current.getStatus()) || !STATUS_S5.equals(target.getStageCode())
                        || !PENDING.equals(target.getStatus()) || current.getVersion() == null
                        || target.getVersion() == null || target.getId() == null) {
                    throw exception(PROJECT_PHASE_SEQUENCE_INVALID);
                }
                return new StageTransition(current, target);
            }
        }
        throw exception(PROJECT_PHASE_SEQUENCE_INVALID);
    }

    private ProjectStageSnapshotDO appendSnapshot(ProjectAcceptanceStageEntryCommand command, Actor actor,
                                                   ProjectMasterDO project, StageTransition transition,
                                                   String operationId, LocalDateTime now) {
        Integer snapshotNo = snapshotMapper.selectNextSnapshotNo(new ProjectStageSnapshotSequenceQuery(
                actor.tenantId(), project.getId(), transition.target().getStageCode()));
        if (snapshotNo == null || snapshotNo <= 0) {
            throw exception(PROJECT_GOVERNANCE_PERSISTENCE_FAILED);
        }
        ProjectStageSnapshotDO snapshot = new ProjectStageSnapshotDO();
        snapshot.setProjectId(project.getId());
        snapshot.setStageCode(transition.target().getStageCode());
        snapshot.setSnapshotNo(snapshotNo);
        snapshot.setOperationType(STAGE_ENTRY);
        snapshot.setBeforeStage(transition.current().getStageCode());
        snapshot.setAfterStage(transition.target().getStageCode());
        snapshot.setBeforeLifecycleStatus(project.getLifecycleStatus());
        snapshot.setAfterLifecycleStatus(project.getLifecycleStatus());
        snapshot.setBeforeAssignmentStatus(project.getAssignmentStatus());
        snapshot.setAfterAssignmentStatus(project.getAssignmentStatus());
        snapshot.setReasonCode("ENTER_ACCEPTANCE_STAGE");
        snapshot.setReasonDetail("进入项目设定验收阶段");
        snapshot.setTreeVersion(command.expectedTreeVersion());
        snapshot.setProviderFactsJson(JsonUtils.toJsonString(Map.of(
                "currentStageStatus", transition.current().getStatus(),
                "targetStageStatus", transition.target().getStatus())));
        snapshot.setOperationId(operationId);
        snapshot.setOperatorUserId(actor.actorId());
        snapshot.setOperatedAt(now);
        snapshot.setCreator(String.valueOf(actor.actorId()));
        snapshot.setUpdater(String.valueOf(actor.actorId()));
        if (snapshotRepository.append(snapshot) != 1 || snapshot.getId() == null) {
            throw exception(PROJECT_GOVERNANCE_PERSISTENCE_FAILED);
        }
        return snapshot;
    }

    private int validateBindings(AcceptanceScopeBindingResult result, Long snapshotId) {
        if (result == null || result.acceptanceFactVersion() == null || result.acceptanceFactVersion() <= 0
                || result.bindings() == null || result.bindings().stream().anyMatch(binding -> binding == null
                || !Objects.equals(binding.projectStageSnapshotId(), snapshotId)
                || binding.deliveryScopeId() == null || binding.deliveryScopeId() <= 0
                || binding.scopeAllocationVersion() == null || binding.scopeAllocationVersion() <= 0
                || !"PROJECT_STAGE_ENTRY".equals(binding.bindingTrigger())
                || !Objects.equals(binding.acceptanceFactVersion(), result.acceptanceFactVersion()))) {
            throw exception(PROJECT_GOVERNANCE_PERSISTENCE_FAILED);
        }
        return result.bindings().size();
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(ProjectAcceptanceStageEntryCommand command,
                                                                   Actor actor,
                                                                   ProjectAcceptanceStageEntryResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", result.projectId());
        detail.put("beforeStage", result.beforeStageCode());
        detail.put("acceptanceStage", result.acceptanceStageCode());
        detail.put("projectVersion", result.projectVersion());
        detail.put("stageSnapshotId", result.projectStageSnapshotId());
        detail.put("bindingCount", result.bindingCount());
        detail.put("operationId", result.operationId());
        detail.put("expectedVersion", command.expectedProjectVersion());
        detail.put("treeVersion", command.expectedTreeVersion());
        Map<String, Object> event = new LinkedHashMap<>(detail);
        event.put("eventId", result.operationId());
        event.put("occurredAt", result.operatedAt());
        return new PlatformCommandExecutionApi.SuccessFacts("PROJECT_ACCEPTANCE_STAGE_ENTRY", "Project",
                String.valueOf(result.projectId()), actor.correlationId(), JsonUtils.toJsonString(detail),
                List.of(new PlatformCommandExecutionApi.BusinessEvent(result.operationId(),
                        "ProjectStageChanged", JsonUtils.toJsonString(event))));
    }

    private static void validate(ProjectAcceptanceStageEntryCommand command, Actor actor) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (command == null || actor == null || !Objects.equals(actor.tenantId(), tenantId)
                || actor.actorId() == null || actor.actorId() <= 0 || blank(actor.correlationId())
                || command.projectId() == null || command.projectId() <= 0
                || command.expectedProjectVersion() == null || command.expectedProjectVersion() < 0
                || command.expectedTreeVersion() == null || command.expectedTreeVersion() <= 0
                || blank(command.idempotencyKey()) || command.idempotencyKey().length() > 128
                || blank(command.requestDigest())) {
            throw exception(PROJECT_GOVERNANCE_STATE_INVALID);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record StageTransition(ProjectStageInstanceDO current, ProjectStageInstanceDO target) {
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
