package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionSelectionQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessStartCommand;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessStartFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectStageSnapshotSequenceQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectExitGateForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageAdvanceUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStagePairForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.repository.projectgovernance.ProjectStageSnapshotRepository;
import cn.iocoder.yudao.module.pms.project.service.stagegate.command.ProjectStageAdvanceCommand;
import cn.iocoder.yudao.module.pms.project.service.stagegate.command.ProjectStageAdvanceResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_ACTION_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_ADVANCE_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_ADVANCE_PERSISTENCE_FAILED;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_GATE_DEPENDENCY_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_GATE_UNSATISFIED;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_PROCESS_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectStageAdvanceApplicationService {

    public static final String ADVANCE_SCOPE = "POST:/api/v1/pms/projects/{id}/actions/advance-stage";
    public static final String START_PROCESS_SCOPE =
            "POST:/api/v1/pms/projects/{id}/stage-gates/{gateReferenceId}/actions/start-process";
    private static final String ACTIVE = "ACTIVE";
    private static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    private static final String PRIMARY = "PRIMARY";

    private final PlatformCommandExecutionApi commandExecutionApi;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final ProjectStageGateProcessOwnerApi processOwnerApi;
    private final ProjectStageGateProviderRegistry providerRegistry;
    private final ProjectMasterMapper projectMapper;
    private final ProjectStageInstanceMapper stageMapper;
    private final ProjectGateInstanceMapper gateMapper;
    private final ProjectGateReferenceInstanceMapper referenceMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectStageSnapshotMapper snapshotMapper;
    private final ProjectStageSnapshotRepository snapshotRepository;

    public List<ProjectStageGateProcessDefinitionFact> listDefinitions(
            Long projectId, Long gateReferenceId, Actor actor) {
        requireUpdatePermission(actor);
        ProjectMasterDO project = requireProject(projectId, actor.tenantId());
        authorizeManageRead(project, actor);
        GateReferenceContext reference = requireProcessReference(project, gateReferenceId, false);
        return processOwnerApi.listSelectableDefinitions(new ProjectStageGateProcessDefinitionSelectionQuery(
                actor.tenantId(), projectId, gateReferenceId, reference.reference().getRefCode()));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectStageGateProcessStartFact startProcess(
            Long projectId, Long gateReferenceId, Integer expectedProjectVersion,
            String selectedProcessDefinitionId, String idempotencyKey, String requestDigest, Actor actor) {
        requireUpdatePermission(actor);
        PlatformCommandExecutionApi.ExecutionResult<ProjectStageGateProcessStartFact> execution =
                commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                                actor.tenantId(), START_PROCESS_SCOPE, actor.actorUserId(), idempotencyKey),
                        requestDigest, ProjectStageGateProcessStartFact.class,
                        () -> startProcessOnce(projectId, gateReferenceId, expectedProjectVersion,
                                selectedProcessDefinitionId, idempotencyKey, requestDigest, actor),
                        result -> processStartSuccessFacts(projectId, gateReferenceId, actor, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        ProjectStageGateProcessStartFact result = execution.response();
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new ProjectStageGateProcessStartFact(result.processInstanceId(), result.processDefinitionId(),
                result.processDefinitionKey(), result.businessKey(), "REPLAYED") : result;
    }

    private ProjectStageGateProcessStartFact startProcessOnce(
            Long projectId, Long gateReferenceId, Integer expectedProjectVersion,
            String selectedProcessDefinitionId, String idempotencyKey, String requestDigest, Actor actor) {
        LockedContext context = lockContext(projectId, expectedProjectVersion, null, actor);
        GateReferenceContext selected = context.references().stream()
                .filter(item -> Objects.equals(item.reference().getId(), gateReferenceId))
                .findFirst().orElseThrow(() -> exception(PROJECT_STAGE_PROCESS_INVALID));
        if (!ProjectStageReadinessService.isProcess(selected.reference())) {
            throw exception(PROJECT_STAGE_PROCESS_INVALID);
        }
        return processOwnerApi.startProcess(new ProjectStageGateProcessStartCommand(
                actor.tenantId(), actor.actorUserId(), projectId, context.project().getCurrentStage(),
                selected.gate().getId(), gateReferenceId, selected.reference().getRefType(),
                selected.reference().getRefCode(), selectedProcessDefinitionId,
                "PROJECT_STAGE_GATE:" + gateReferenceId, idempotencyKey, requestDigest, Map.of()));
    }

    public ProjectStageAdvanceResult advance(ProjectStageAdvanceCommand command, Actor actor) {
        requireUpdatePermission(actor);
        PlatformCommandExecutionApi.ExecutionResult<ProjectStageAdvanceResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(), ADVANCE_SCOPE,
                        actor.actorUserId(), command.idempotencyKey()),
                command.requestDigest(), ProjectStageAdvanceResult.class,
                () -> advanceOnce(command, actor), result -> successFacts(command, actor, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        ProjectStageAdvanceResult result = execution.response();
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new ProjectStageAdvanceResult(result.projectId(), result.beforeStage(), result.afterStage(),
                result.projectVersion(), result.stageSnapshotId(), result.gateEvaluationSummary(),
                result.operationId(), result.operatedAt(), true) : result;
    }

    private ProjectStageAdvanceResult advanceOnce(ProjectStageAdvanceCommand command, Actor actor) {
        LockedContext context = lockContext(command.projectId(), command.expectedProjectVersion(),
                command.expectedTreeVersion(), actor);
        if (!Objects.equals(context.project().getCurrentStage(), command.expectedCurrentStage())) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        List<Map<String, Object>> evaluations = new ArrayList<>();
        Map<Long, List<GateReferenceContext>> byGate = context.references().stream()
                .collect(Collectors.groupingBy(item -> item.gate().getId(), LinkedHashMap::new, Collectors.toList()));
        for (ProjectGateInstanceDO gate : context.gates()) {
            List<GateReferenceContext> gateRefs = byGate.getOrDefault(gate.getId(), List.of());
            if (gateRefs.isEmpty()) throw exception(PROJECT_STAGE_GATE_DEPENDENCY_UNAVAILABLE, "EXIT Gate缺少Reference");
            for (GateReferenceContext item : gateRefs) {
                String providerKey = ProjectStageReadinessService.providerKey(item.reference().getRefType());
                if (providerKey == null) {
                    throw exception(PROJECT_STAGE_GATE_DEPENDENCY_UNAVAILABLE, "OWNER_PROVIDER_UNKNOWN");
                }
                ProjectStageGateFact fact;
                try {
                    fact = providerRegistry.lockAndRevalidate(providerKey, factQuery(context, item));
                } catch (IllegalStateException ex) {
                    throw exception(PROJECT_STAGE_GATE_DEPENDENCY_UNAVAILABLE, "OWNER_PROVIDER_UNAVAILABLE");
                }
                evaluations.add(evaluation(item, fact));
                if (fact.outcome() == ProjectStageGateOutcome.VERSION_CONFLICT) {
                    throw exception(PROJECT_VERSION_CONFLICT);
                }
                if (fact.outcome() == ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE) {
                    throw exception(PROJECT_STAGE_GATE_DEPENDENCY_UNAVAILABLE, fact.unmetCode());
                }
                if (fact.outcome() != ProjectStageGateOutcome.SATISFIED) {
                    throw exception(PROJECT_STAGE_GATE_UNSATISFIED, fact.unmetCode());
                }
            }
        }
        String updater = String.valueOf(actor.actorUserId());
        for (ProjectGateInstanceDO gate : context.gates()) {
            if (gateMapper.updateStatusIfMatch(new ProjectGateStatusUpdate(actor.tenantId(), gate.getId(),
                    gate.getVersion(), gate.getStatus(), "PASSED", updater)) != 1) {
                throw exception(PROJECT_VERSION_CONFLICT);
            }
        }
        if (stageMapper.updateStatusIfMatch(new ProjectStageStatusUpdate(actor.tenantId(),
                context.pair().current().getId(), context.pair().current().getVersion(),
                "ACTIVE", "DONE", updater)) != 1
                || stageMapper.updateStatusIfMatch(new ProjectStageStatusUpdate(actor.tenantId(),
                context.pair().next().getId(), context.pair().next().getVersion(),
                "PENDING", "ACTIVE", updater)) != 1
                || projectMapper.advanceStageIfMatch(new ProjectStageAdvanceUpdate(actor.tenantId(),
                context.project().getId(), context.project().getVersion(), context.project().getCurrentStage(),
                context.pair().next().getStageCode(), updater)) != 1) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        String operationId = UUID.randomUUID().toString();
        LocalDateTime operatedAt = LocalDateTime.now();
        String evaluationJson = JsonUtils.toJsonString(evaluations);
        ProjectStageSnapshotDO snapshot = snapshot(context, actor, operationId, operatedAt, evaluationJson);
        if (snapshotRepository.append(snapshot) != 1 || snapshot.getId() == null) {
            throw exception(PROJECT_STAGE_ADVANCE_PERSISTENCE_FAILED);
        }
        return new ProjectStageAdvanceResult(context.project().getId(), context.project().getCurrentStage(),
                context.pair().next().getStageCode(), context.project().getVersion() + 1,
                snapshot.getId(), evaluationJson, operationId, operatedAt, false);
    }

    private LockedContext lockContext(Long projectId, Integer expectedProjectVersion,
                                      Long expectedTreeVersion, Actor actor) {
        ProjectMasterDO project = projectMapper.selectByIdForUpdate(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) throw exception(PROJECT_NOT_EXISTS);
        if (!Objects.equals(project.getVersion(), expectedProjectVersion)) throw exception(PROJECT_VERSION_CONFLICT);
        ProjectScopeResult initial = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(),
                actor.actorUserId(), projectId, ProjectScopeApi.ACTION_MANAGE));
        long treeVersion = expectedTreeVersion == null ? initial.treeVersion() : expectedTreeVersion;
        ProjectScopeResult locked = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                actor.tenantId(), actor.actorUserId(), projectId, ProjectScopeApi.ACTION_MANAGE, treeVersion));
        if (!Objects.equals(locked.treeVersion(), treeVersion) || !locked.fullProjectIds().contains(projectId)) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        requireCurrentManager(project, actor);
        List<ProjectStageInstanceDO> stages = stageMapper.selectStagePairForUpdate(new ProjectStagePairForUpdateQuery(
                actor.tenantId(), projectId, project.getCurrentStage()));
        ProjectStageReadinessService.StagePair pair = ProjectStageReadinessService.requirePair(project, stages);
        List<ProjectGateInstanceDO> gates = gateMapper.selectExitGatesForUpdate(new ProjectExitGateForUpdateQuery(
                actor.tenantId(), projectId, project.getCurrentStage()));
        if (gates.isEmpty()) throw exception(PROJECT_STAGE_ADVANCE_INVALID, "当前阶段没有EXIT Gate");
        List<ProjectGateReferenceInstanceDO> references = referenceMapper.selectOrderedForUpdate(
                new ProjectGateReferenceForUpdateQuery(actor.tenantId(), gates.stream().map(ProjectGateInstanceDO::getId).toList()));
        Map<Long, ProjectGateInstanceDO> gateById = gates.stream().collect(Collectors.toMap(ProjectGateInstanceDO::getId, it -> it));
        List<GateReferenceContext> contexts = references.stream()
                .map(ref -> new GateReferenceContext(gateById.get(ref.getGateId()), ref)).toList();
        if (contexts.stream().anyMatch(it -> it.gate() == null)) throw exception(PROJECT_STAGE_ADVANCE_INVALID, "Gate Reference身份不一致");
        return new LockedContext(project, pair, gates, contexts, treeVersion);
    }

    private void authorizeManageRead(ProjectMasterDO project, Actor actor) {
        if (!ACTIVE.equals(project.getLifecycleStatus())
                || !Set.of("S0", "S1", "S2", "S3").contains(project.getCurrentStage())) {
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "项目当前阶段不可使用通用Gate流程");
        }
        ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(),
                actor.actorUserId(), project.getId(), ProjectScopeApi.ACTION_MANAGE));
        if (!scope.fullProjectIds().contains(project.getId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        try {
            participantFactApi.inspect(new ProjectParticipantFactQuery(project.getId(), actor.actorUserId(),
                    Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), LocalDateTime.now()));
        } catch (RuntimeException ex) {
            throw exception(PROJECT_STAGE_ACTION_FORBIDDEN);
        }
    }

    private void requireCurrentManager(ProjectMasterDO project, Actor actor) {
        if (Objects.equals(project.getManagerId(), actor.actorUserId())) return;
        List<ProjectMemberAssignmentDO> managers = memberMapper.selectParticipantFactsForUpdate(
                new ProjectParticipantFactLockQuery(actor.tenantId(), project.getId(), actor.actorUserId(), Set.of(PROJECT_MANAGER)));
        if (managers.size() != 1) throw exception(PROJECT_STAGE_ACTION_FORBIDDEN);
        String assignmentType = managers.getFirst().getAssignmentType();
        if (assignmentType != null && !PRIMARY.equals(assignmentType)) {
            throw exception(PROJECT_STAGE_ACTION_FORBIDDEN);
        }
    }

    private GateReferenceContext requireProcessReference(ProjectMasterDO project, Long referenceId, boolean locked) {
        List<ProjectGateInstanceDO> gates = locked
                ? gateMapper.selectExitGatesForUpdate(new ProjectExitGateForUpdateQuery(project.getTenantId(), project.getId(), project.getCurrentStage()))
                : gateMapper.selectExitGates(new ProjectExitGateForUpdateQuery(project.getTenantId(), project.getId(), project.getCurrentStage()));
        if (gates.isEmpty()) throw exception(PROJECT_STAGE_PROCESS_INVALID);
        List<ProjectGateReferenceInstanceDO> refs = locked
                ? referenceMapper.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(project.getTenantId(), gates.stream().map(ProjectGateInstanceDO::getId).toList()))
                : referenceMapper.selectOrdered(new ProjectGateReferenceForUpdateQuery(project.getTenantId(), gates.stream().map(ProjectGateInstanceDO::getId).toList()));
        Map<Long, ProjectGateInstanceDO> byId = gates.stream().collect(Collectors.toMap(ProjectGateInstanceDO::getId, it -> it));
        ProjectGateReferenceInstanceDO ref = refs.stream().filter(it -> Objects.equals(it.getId(), referenceId)).findFirst()
                .orElseThrow(() -> exception(PROJECT_STAGE_PROCESS_INVALID));
        if (!ProjectStageReadinessService.isProcess(ref)) throw exception(PROJECT_STAGE_PROCESS_INVALID);
        return new GateReferenceContext(byId.get(ref.getGateId()), ref);
    }

    private ProjectStageGateFactQuery factQuery(LockedContext context, GateReferenceContext item) {
        return new ProjectStageGateFactQuery(context.project().getTenantId(), context.project().getId(),
                context.project().getCurrentStage(), item.gate().getId(), item.gate().getGateCode(),
                item.gate().getVersion(), item.reference().getId(), item.reference().getVersion(),
                item.reference().getRefType(), item.reference().getRefCode());
    }

    private static Map<String, Object> evaluation(GateReferenceContext item, ProjectStageGateFact fact) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("gateId", item.gate().getId());
        value.put("gateCode", item.gate().getGateCode());
        value.put("gateReferenceId", item.reference().getId());
        value.put("refType", item.reference().getRefType());
        value.put("refCode", item.reference().getRefCode());
        value.put("providerKey", fact.providerKey());
        value.put("ownerObjectKey", fact.ownerObjectKey());
        value.put("ownerBusinessVersion", fact.ownerBusinessVersion());
        value.put("factVersion", fact.factVersion());
        value.put("outcome", fact.outcome());
        value.put("unmetCode", fact.unmetCode());
        return value;
    }

    private ProjectStageSnapshotDO snapshot(LockedContext context, Actor actor, String operationId,
                                            LocalDateTime operatedAt, String evaluations) {
        Integer no = snapshotMapper.selectNextSnapshotNo(new ProjectStageSnapshotSequenceQuery(
                actor.tenantId(), context.project().getId(), context.pair().next().getStageCode()));
        if (no == null || no <= 0) throw exception(PROJECT_STAGE_ADVANCE_PERSISTENCE_FAILED);
        ProjectStageSnapshotDO snapshot = new ProjectStageSnapshotDO();
        snapshot.setProjectId(context.project().getId());
        snapshot.setStageCode(context.pair().next().getStageCode());
        snapshot.setSnapshotNo(no);
        snapshot.setOperationType("STAGE_ADVANCE");
        snapshot.setBeforeStage(context.project().getCurrentStage());
        snapshot.setAfterStage(context.pair().next().getStageCode());
        snapshot.setBeforeLifecycleStatus(context.project().getLifecycleStatus());
        snapshot.setAfterLifecycleStatus(context.project().getLifecycleStatus());
        snapshot.setBeforeAssignmentStatus(context.project().getAssignmentStatus());
        snapshot.setAfterAssignmentStatus(context.project().getAssignmentStatus());
        snapshot.setReasonCode("ALL_EXIT_GATES_SATISFIED");
        snapshot.setReasonDetail("当前阶段全部EXIT Gate已由Owner重验通过");
        snapshot.setGuardSnapshotJson(evaluations);
        snapshot.setProviderFactsJson(evaluations);
        snapshot.setTreeVersion(context.treeVersion());
        snapshot.setOperationId(operationId);
        snapshot.setOperatorUserId(actor.actorUserId());
        snapshot.setOperatedAt(operatedAt);
        snapshot.setCreator(String.valueOf(actor.actorUserId()));
        snapshot.setUpdater(String.valueOf(actor.actorUserId()));
        return snapshot;
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(ProjectStageAdvanceCommand command, Actor actor,
                                                                   ProjectStageAdvanceResult result) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", result.operationId());
        event.put("tenantId", actor.tenantId());
        event.put("projectId", result.projectId());
        event.put("projectVersion", result.projectVersion());
        event.put("action", "STAGE_ADVANCE");
        event.put("beforeStage", result.beforeStage());
        event.put("afterStage", result.afterStage());
        event.put("stageSnapshotId", result.stageSnapshotId());
        event.put("gateEvaluationSummaryRef", result.stageSnapshotId());
        event.put("actorUserId", actor.actorUserId());
        event.put("occurredAt", result.operatedAt());
        Map<String, Object> detail = new LinkedHashMap<>(event);
        detail.put("expectedProjectVersion", command.expectedProjectVersion());
        detail.put("expectedTreeVersion", command.expectedTreeVersion());
        detail.put("idempotencyKey", command.idempotencyKey());
        return new PlatformCommandExecutionApi.SuccessFacts("PROJECT_STAGE_ADVANCE", "Project",
                String.valueOf(result.projectId()), actor.correlationId(), JsonUtils.toJsonString(detail),
                List.of(new PlatformCommandExecutionApi.BusinessEvent(result.operationId(),
                        "ProjectStageChanged", JsonUtils.toJsonString(event))));
    }

    private PlatformCommandExecutionApi.SuccessFacts processStartSuccessFacts(
            Long projectId, Long gateReferenceId, Actor actor, ProjectStageGateProcessStartFact result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", projectId);
        detail.put("gateReferenceId", gateReferenceId);
        detail.put("processInstanceId", result.processInstanceId());
        detail.put("processDefinitionId", result.processDefinitionId());
        detail.put("processDefinitionKey", result.processDefinitionKey());
        detail.put("businessKey", result.businessKey());
        return new PlatformCommandExecutionApi.SuccessFacts("PROJECT_STAGE_GATE_PROCESS_START", "Project",
                String.valueOf(projectId), actor.correlationId(), JsonUtils.toJsonString(detail), List.of());
    }

    private ProjectMasterDO requireProject(Long projectId, Long tenantId) {
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) throw exception(PROJECT_NOT_EXISTS);
        return project;
    }

    private void requireUpdatePermission(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorUserId() == null
                || !permissionApi.hasAnyPermissions(actor.actorUserId(), "pms:project:update")) {
            throw exception(PROJECT_STAGE_ACTION_FORBIDDEN);
        }
    }

    public record Actor(Long tenantId, Long actorUserId, String correlationId) {
        public Actor {
            if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId())) {
                throw new IllegalArgumentException("actor tenant must match trusted tenant context");
            }
        }
    }

    private record GateReferenceContext(ProjectGateInstanceDO gate, ProjectGateReferenceInstanceDO reference) {
    }

    private record LockedContext(ProjectMasterDO project, ProjectStageReadinessService.StagePair pair,
                                 List<ProjectGateInstanceDO> gates, List<GateReferenceContext> references,
                                 Long treeVersion) {
    }
}
