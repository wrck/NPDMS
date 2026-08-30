package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.adapter;

import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceContractException;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/** 组合PROJ参与者事实与ACTION_EDIT范围，不以数据范围替代项目内角色。 */
@Component
@RequiredArgsConstructor
public class ProjectQualificationApiAdapter implements ProjectQualificationPort {

    static final Set<String> REQUIRED_PROJECT_ROLES = Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
    private static final String ACTIVE = "ACTIVE";
    private static final String ARRIVAL_STAGE = "S4";

    private final ProjectParticipantFactApi participantFactApi;
    private final ProjectScopeApi projectScopeApi;

    @Override
    @Transactional(readOnly = true)
    public ProjectQualificationFact inspect(Long tenantId, Long projectId, Long actorUserId) {
        requireIdentity(tenantId, projectId, actorUserId);
        try {
            ProjectParticipantFact participant = participantFactApi.inspect(new ProjectParticipantFactQuery(
                    projectId, null, REQUIRED_PROJECT_ROLES, LocalDateTime.now()));
            requireParticipantIdentity(participant, projectId, null);
            requireEligibleArrivalProject(participant, null, null);
            ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                    tenantId, actorUserId, projectId, ProjectScopeApi.ACTION_EDIT));
            requireEditableScope(scope, projectId, null);
            return toFact(participant, scope.treeVersion());
        } catch (ArrivalAcceptanceContractException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ownerUnavailable(exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectQualificationFact lockAndRevalidate(RevalidationCommand command) {
        Objects.requireNonNull(command, "project qualification revalidation command is required");
        if (command.requireActorAsProjectManager()
                && !Objects.equals(command.actorUserId(), command.subjectUserId())) {
            throw ArrivalAcceptanceContractException.simple("BUSINESS_GATE_INVALID",
                    "PROJECT_SUBJECT_NOT_ELIGIBLE", "actor is not the frozen project manager");
        }
        try {
            ProjectParticipantFact participant = participantFactApi.lockAndRevalidate(
                    new ProjectParticipantFactRevalidationQuery(
                            command.projectId(), command.subjectUserId(), command.expectedProjectVersion(),
                            ACTIVE, null, REQUIRED_PROJECT_ROLES));
            requireParticipantIdentity(participant, command.projectId(), command.subjectUserId());
            requireEligibleArrivalProject(participant, command.expectedProjectVersion(), command.expectedFactVersion());
            ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                    command.tenantId(), command.actorUserId(), command.projectId(),
                    ProjectScopeApi.ACTION_EDIT, command.expectedScopeVersion()));
            requireEditableScope(scope, command.projectId(), command.expectedScopeVersion());
            return toFact(participant, scope.treeVersion());
        } catch (ArrivalAcceptanceContractException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ownerUnavailable(exception);
        }
    }

    private static ProjectQualificationFact toFact(ProjectParticipantFact participant, Long scopeVersion) {
        return new ProjectQualificationFact(
                participant.projectId(), participant.userId(), participant.effectiveRoleCodes(),
                participant.lifecycleStatus(), participant.currentStage(), participant.projectVersion(),
                participant.factVersion(), scopeVersion);
    }

    private static void requireIdentity(Long tenantId, Long projectId, Long actorUserId) {
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || actorUserId == null || actorUserId <= 0) {
            throw new IllegalArgumentException("invalid project qualification identity");
        }
    }

    private static void requireParticipantIdentity(ProjectParticipantFact participant,
                                                   Long projectId, Long subjectUserId) {
        if (participant == null || !Objects.equals(projectId, participant.projectId())
                || subjectUserId != null && !Objects.equals(subjectUserId, participant.userId())
                || participant.userId() == null || participant.userId() <= 0
                || participant.effectiveRoleCodes() == null || participant.effectiveRoleCodes().isEmpty()) {
            throw ArrivalAcceptanceContractException.owner("OWNER_PROVIDER_UNAVAILABLE",
                    "PROJ_PROVIDER_UNAVAILABLE", "PROJ", "project participant fact is unavailable");
        }
    }

    private static void requireEligibleArrivalProject(ProjectParticipantFact participant,
                                                      Integer expectedProjectVersion,
                                                      Long expectedFactVersion) {
        if (!ACTIVE.equals(participant.lifecycleStatus())) throw ArrivalAcceptanceContractException.simple(
                "BUSINESS_GATE_INVALID", "PROJECT_NOT_ACTIVE", "project is not active");
        if (!ARRIVAL_STAGE.equals(participant.currentStage())) throw ArrivalAcceptanceContractException.simple(
                "BUSINESS_GATE_INVALID", "PROJECT_STAGE_NOT_S4", "project is not in S4");
        if (!participant.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)) {
            throw ArrivalAcceptanceContractException.simple("BUSINESS_GATE_INVALID",
                    "PROJECT_SUBJECT_NOT_ELIGIBLE", "project subject is not eligible");
        }
        if (expectedProjectVersion != null && !Objects.equals(expectedProjectVersion, participant.projectVersion())
                || expectedFactVersion != null && !Objects.equals(expectedFactVersion, participant.factVersion())) {
            throw ArrivalAcceptanceContractException.owner("SCOPE_STALE", "PROJECT_FACT_STALE",
                    "PROJ", "project qualification fact is stale");
        }
    }

    private static void requireEditableScope(ProjectScopeResult scope, Long projectId,
                                             Long expectedScopeVersion) {
        if (scope == null || scope.treeVersion() == null || scope.treeVersion() < 0) {
            throw ArrivalAcceptanceContractException.owner("OWNER_PROVIDER_UNAVAILABLE",
                    "PROJ_PROVIDER_UNAVAILABLE", "PROJ", "project scope fact is unavailable");
        }
        if (expectedScopeVersion != null && !expectedScopeVersion.equals(scope.treeVersion())) {
            throw ArrivalAcceptanceContractException.owner("SCOPE_STALE", "PROJECT_FACT_STALE",
                    "PROJ", "project scope fact is stale");
        }
        if (scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)
                || scope.placeholderProjectIds() != null && scope.placeholderProjectIds().contains(projectId)) {
            throw ArrivalAcceptanceContractException.simple("DATA_SCOPE_FORBIDDEN",
                    "PROJECT_DATA_SCOPE_DENIED", "project is outside the current edit scope");
        }
    }

    private static ArrivalAcceptanceContractException ownerUnavailable(RuntimeException cause) {
        var exception = ArrivalAcceptanceContractException.owner("OWNER_PROVIDER_UNAVAILABLE",
                "PROJ_PROVIDER_UNAVAILABLE", "PROJ", "project owner provider is unavailable");
        exception.initCause(cause);
        return exception;
    }
}
