package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectExitGateForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStagePairForUpdateQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_STAGE_ADVANCE_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class ProjectStageReadinessService {

    private final cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver runtimeGraphResolver;

    private final ProjectMasterMapper projectMapper;
    private final ProjectStageInstanceMapper stageMapper;
    private final ProjectGateInstanceMapper gateMapper;
    private final ProjectGateReferenceInstanceMapper referenceMapper;
    private final ProjectStageGateProviderRegistry providerRegistry;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final PermissionApi permissionApi;
    private final ProjectMemberAssignmentMapper memberMapper;

    @Transactional(rollbackFor = Exception.class)
    public ProjectStageReadinessResult evaluate(Long projectId, Long actorUserId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        ProjectScopeResult viewScope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, actorUserId, projectId, ProjectScopeApi.ACTION_VIEW));
        if (!viewScope.fullProjectIds().contains(projectId)) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        var graph = runtimeGraphResolver.inspect(project);
        List<ProjectGateInstanceDO> gates = graph.gates();
        List<ProjectGateReferenceInstanceDO> references = graph.references();
        Map<Long, List<ProjectGateReferenceInstanceDO>> byGate = references.stream()
                .collect(Collectors.groupingBy(ProjectGateReferenceInstanceDO::getGateId));
        boolean canManage = canManage(project, actorUserId, tenantId);
        List<ProjectStageReadinessResult.GateResult> results = new ArrayList<>();
        for (ProjectGateInstanceDO gate : gates) {
            List<ProjectGateReferenceInstanceDO> gateRefs = byGate.getOrDefault(gate.getId(), List.of());
            if (gateRefs.isEmpty()) {
                throw exception(PROJECT_STAGE_ADVANCE_INVALID, "EXIT Gate缺少Reference");
            }
            List<ProjectStageReadinessResult.ReferenceResult> refResults = new ArrayList<>();
            boolean gateSatisfied = true;
            for (ProjectGateReferenceInstanceDO reference : gateRefs) {
                ProjectStageGateFact fact = evaluateFact(tenantId, project, gate, reference);
                gateSatisfied &= fact.outcome() == ProjectStageGateOutcome.SATISFIED;
                List<String> actions = canManage && isProcess(reference)
                        && fact.outcome() == ProjectStageGateOutcome.UNSATISFIED
                        && isNotStarted(fact.unmetCode())
                        ? List.of("START_PROCESS") : List.of();
                refResults.add(new ProjectStageReadinessResult.ReferenceResult(reference.getId(),
                        reference.getRefType(), reference.getRefCode(), fact, actions));
            }
            results.add(new ProjectStageReadinessResult.GateResult(gate.getId(), gate.getGateCode(),
                    gate.getName(), gate.getStatus(), gateSatisfied, List.copyOf(refResults)));
        }
        boolean allSatisfied = results.stream().allMatch(ProjectStageReadinessResult.GateResult::satisfied);
        boolean resolved = graph.transition().status() == cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.Status.RESOLVED;
        boolean completed = graph.completion() == cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.ConditionStatus.SATISFIED;
        String assignmentReason = s0AssignmentUnmetReason(project, memberMapper);
        boolean allowed = canManage && allSatisfied && resolved && completed && assignmentReason == null;
        String reason = assignmentReason != null ? assignmentReason : !resolved ? graph.transition().status().name()
                : !completed ? "STAGE_COMPLETION_" + graph.completion().name() : "请完成当前准出及目标准入条件";
        return new ProjectStageReadinessResult(projectId, project.getVersion(), viewScope.treeVersion(),
                graph.current().getStageCode(), graph.target() == null ? null : graph.target().getStageCode(), allowed,
                allowed ? null : reason, List.copyOf(results));
    }

    /** PM-01/PM-08: S0 has no assignment task; both real responsibilities are mandatory.
     * Commands call this again while holding the project root lock used by member writers.
     */
    static String s0AssignmentUnmetReason(ProjectMasterDO project, ProjectMemberAssignmentMapper memberMapper) {
        return s0AssignmentUnmetReason(project, memberMapper, false);
    }

    static String s0AssignmentUnmetReason(ProjectMasterDO project, ProjectMemberAssignmentMapper memberMapper, boolean locked) {
        if (!"S0".equals(project.getCurrentStage())) return null;
        if (!Objects.equals(project.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now();
        List<ProjectMemberAssignmentDO> candidates = memberMapper.selectActiveForAssignmentState(
                new ProjectAssignmentStateQuery(project.getId(), now));
        if (locked) {
            // A repeatable-read snapshot may predate the root lock: never authorize from stale intervals.
            candidates = candidates.stream().filter(row -> Objects.equals(row.getTenantId(), project.getTenantId())
                            && Objects.equals(row.getProjectId(), project.getId()) && row.getUserId() != null
                            && (Objects.equals(row.getUserId(), project.getManagerId())
                            || cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.isServiceManager(row.getMemberRole())))
                    .map(ProjectMemberAssignmentDO::getUserId).distinct().sorted()
                    .flatMap(userId -> memberMapper.selectActiveByUserForUpdate(
                            new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberForUpdateQuery(
                                    project.getTenantId(), project.getId(), userId, now)).stream()).toList();
        }
        List<ProjectMemberAssignmentDO> active = candidates.stream()
                .filter(row -> Objects.equals(row.getTenantId(), project.getTenantId())
                        && Objects.equals(row.getProjectId(), project.getId())
                        && row.getUserId() != null && !Boolean.TRUE.equals(row.getDeleted())
                        && "ACTIVE".equals(row.getStatus())
                        && (row.getEffectiveFrom() == null || !row.getEffectiveFrom().isAfter(now))
                        && (row.getEffectiveTo() == null || row.getEffectiveTo().isAfter(now))).toList();
        // PM primary is the current project pointer, not the historical assignment_type label.
        boolean projectManager = project.getManagerId() != null && active.stream().anyMatch(row ->
                "PROJECT_MANAGER".equals(row.getMemberRole()) && Objects.equals(row.getUserId(), project.getManagerId()));
        // Match the existing assignment-state writer, including legacy null PRIMARY labels.
        boolean serviceManager = active.stream().anyMatch(row ->
                cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.isServiceManager(row.getMemberRole())
                        && (row.getAssignmentType() == null || "PRIMARY".equals(row.getAssignmentType())));
        if (!projectManager && !serviceManager) return "S0_PRIMARY_MANAGERS_REQUIRED";
        if (!serviceManager) return "S0_PRIMARY_SERVICE_MANAGER_REQUIRED";
        if (!projectManager) return "S0_PRIMARY_PROJECT_MANAGER_REQUIRED";
        return "ASSIGNED".equals(project.getAssignmentStatus()) ? null : "S0_ASSIGNMENT_STATUS_NOT_ASSIGNED";
    }

    private boolean canManage(ProjectMasterDO project, Long actorUserId, Long tenantId) {
        if (!permissionApi.hasAnyPermissions(actorUserId, "pms:project:update")) {
            return false;
        }
        try {
            ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                    tenantId, actorUserId, project.getId(), ProjectScopeApi.ACTION_MANAGE));
            if (!scope.fullProjectIds().contains(project.getId())) return false;
            var manager = participantFactApi.inspect(new ProjectParticipantFactQuery(project.getId(), actorUserId,
                    Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), LocalDateTime.now()));
            return Objects.equals(manager.userId(), actorUserId);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private ProjectStageGateFact evaluateFact(Long tenantId, ProjectMasterDO project,
                                              ProjectGateInstanceDO gate,
                                              ProjectGateReferenceInstanceDO reference) {
        String key = providerKey(reference.getRefType());
        if (key == null) throw exception(PROJECT_STAGE_ADVANCE_INVALID, "未知Gate Reference类型");
        try {
            return providerRegistry.lockAndRevalidate(key, new ProjectStageGateFactQuery(
                    tenantId, project.getId(), gate.getStageCode(), gate.getId(), gate.getGateCode(),
                    gate.getVersion(), reference.getId(), reference.getVersion(),
                    reference.getRefType(), reference.getRefCode()));
        } catch (IllegalStateException unavailable) {
            return new ProjectStageGateFact(key, reference.getRefType(), reference.getRefCode(),
                    "UNKNOWN", "UNKNOWN", ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE, "OWNER_PROVIDER_UNAVAILABLE");
        }
    }

    static StagePair requirePair(ProjectMasterDO project, List<ProjectStageInstanceDO> stages) {
        if (!"ACTIVE".equals(project.getLifecycleStatus())
                || stages == null || stages.size() != 2
                || !Objects.equals(stages.get(0).getStageCode(), project.getCurrentStage())
                || Objects.equals(stages.get(0).getStageCode(), stages.get(1).getStageCode())
                || !"ACTIVE".equals(stages.get(0).getStatus())
                || !"PENDING".equals(stages.get(1).getStatus())) {
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "项目当前阶段不可使用通用推进");
        }
        return new StagePair(stages.get(0), stages.get(1));
    }

    static boolean isProcess(ProjectGateReferenceInstanceDO reference) {
        return "PROCESS".equals(reference.getRefType()) || "APPROVAL".equals(reference.getRefType());
    }

    private static boolean isNotStarted(String unmetCode) {
        return "APPROVAL_NOT_STARTED".equals(unmetCode) || "PROCESS_NOT_STARTED".equals(unmetCode);
    }

    public static String providerKey(String refType) {
        return switch (refType) {
            case "TASK" -> ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK;
            case "MILESTONE" -> ProjectStageGateFactProviderApi.PROVIDER_PROJ_MILESTONE;
            case "DELIVERABLE" -> ProjectStageGateFactProviderApi.PROVIDER_ACC_DELIVERABLE;
            case "STATE" -> ProjectStageGateFactProviderApi.PROVIDER_PROJ_STATE;
            case "APPROVAL" -> ProjectStageGateFactProviderApi.PROVIDER_BPM_APPROVAL;
            case "PROCESS" -> ProjectStageGateFactProviderApi.PROVIDER_BPM_PROCESS;
            default -> null;
        };
    }

    record StagePair(ProjectStageInstanceDO current, ProjectStageInstanceDO next) {
    }
}
