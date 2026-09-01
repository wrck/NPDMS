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

    private static final Map<String, String> NEXT_STAGE_CODES = Map.of(
            "S0", "S1", "S1", "S2", "S2", "S3", "S3", "S4");

    private final ProjectMasterMapper projectMapper;
    private final ProjectStageInstanceMapper stageMapper;
    private final ProjectGateInstanceMapper gateMapper;
    private final ProjectGateReferenceInstanceMapper referenceMapper;
    private final ProjectStageGateProviderRegistry providerRegistry;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final PermissionApi permissionApi;

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
        List<ProjectStageInstanceDO> stages = stageMapper.selectStagePair(
                new ProjectStagePairForUpdateQuery(tenantId, projectId, project.getCurrentStage()));
        StagePair pair = requirePair(project, stages);
        List<ProjectGateInstanceDO> gates = gateMapper.selectExitGates(
                new ProjectExitGateForUpdateQuery(tenantId, projectId, project.getCurrentStage()));
        if (gates.isEmpty()) {
            throw exception(PROJECT_STAGE_ADVANCE_INVALID, "当前阶段没有EXIT Gate");
        }
        List<ProjectGateReferenceInstanceDO> references = referenceMapper.selectOrdered(
                new ProjectGateReferenceForUpdateQuery(tenantId, gates.stream().map(ProjectGateInstanceDO::getId).toList()));
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
        boolean allowed = canManage && allSatisfied;
        return new ProjectStageReadinessResult(projectId, project.getVersion(), viewScope.treeVersion(),
                pair.current().getStageCode(), pair.next().getStageCode(), allowed,
                allowed ? null : "请完成当前阶段全部准出条件", List.copyOf(results));
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
        return providerRegistry.lockAndRevalidate(key, new ProjectStageGateFactQuery(
                tenantId, project.getId(), project.getCurrentStage(), gate.getId(), gate.getGateCode(),
                gate.getVersion(), reference.getId(), reference.getVersion(),
                reference.getRefType(), reference.getRefCode()));
    }

    static StagePair requirePair(ProjectMasterDO project, List<ProjectStageInstanceDO> stages) {
        if (!"ACTIVE".equals(project.getLifecycleStatus())
                || !Set.of("S0", "S1", "S2", "S3").contains(project.getCurrentStage())
                || stages == null || stages.size() != 2
                || !Objects.equals(stages.get(0).getStageCode(), project.getCurrentStage())
                || !Objects.equals(stages.get(1).getStageCode(), NEXT_STAGE_CODES.get(project.getCurrentStage()))
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

    static String providerKey(String refType) {
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
