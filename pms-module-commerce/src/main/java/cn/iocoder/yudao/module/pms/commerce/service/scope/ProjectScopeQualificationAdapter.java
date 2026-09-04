package cn.iocoder.yudao.module.pms.commerce.service.scope;

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

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException.Code.*;

/** PROJ当前项目经理事实与ACTION_EDIT范围的组合适配，不以任一事实替代另一事实。 */
@Component
@RequiredArgsConstructor
public class ProjectScopeQualificationAdapter {

    private static final Set<String> PROJECT_MANAGER = Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);

    private final ProjectParticipantFactApi participantFactApi;
    private final ProjectScopeApi projectScopeApi;

    public Snapshot inspect(Long tenantId, Long projectId, Long actorId) {
        requireIdentity(tenantId, projectId, actorId);
        try {
            ProjectParticipantFact participant = participantFactApi.inspect(
                    new ProjectParticipantFactQuery(projectId, actorId, PROJECT_MANAGER, LocalDateTime.now()));
            ProjectScopeResult scope = projectScopeApi.resolveCurrent(
                    new ProjectCurrentScopeQuery(tenantId, actorId, projectId, ProjectScopeApi.ACTION_EDIT));
            return requireEligible(projectId, actorId, participant, scope, null);
        } catch (CommerceDeliveryScopeCommandException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CommerceDeliveryScopeCommandException(OWNER_PROVIDER_UNAVAILABLE,
                    "PROJ资格事实不可用", exception);
        }
    }

    public Snapshot lockAndRevalidate(Snapshot expected) {
        Objects.requireNonNull(expected, "expected project snapshot");
        try {
            ProjectParticipantFact participant = participantFactApi.lockAndRevalidate(
                    new ProjectParticipantFactRevalidationQuery(expected.projectId(), expected.managerUserId(),
                            expected.projectVersion(), null, null, PROJECT_MANAGER));
            ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                    expected.tenantId(), expected.managerUserId(), expected.projectId(),
                    ProjectScopeApi.ACTION_EDIT, expected.treeVersion()));
            return requireEligible(expected.projectId(), expected.managerUserId(), participant, scope, expected);
        } catch (CommerceDeliveryScopeCommandException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CommerceDeliveryScopeCommandException(OWNER_PROVIDER_UNAVAILABLE,
                    "PROJ资格锁定失败", exception);
        }
    }

    private Snapshot requireEligible(Long projectId, Long actorId, ProjectParticipantFact participant,
                                     ProjectScopeResult scope, Snapshot expected) {
        if (participant == null || !Objects.equals(projectId, participant.projectId())
                || !Objects.equals(actorId, participant.userId())
                || participant.effectiveRoleCodes() == null
                || !participant.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)) {
            throw new CommerceDeliveryScopeCommandException(PROJECT_SUBJECT_NOT_ELIGIBLE,
                    "当前主体不是该项目的项目经理");
        }
        if (participant.projectVersion() == null || participant.projectVersion() < 0
                || participant.factVersion() == null || participant.factVersion() < 0
                || scope == null || scope.treeVersion() == null || scope.treeVersion() < 0) {
            throw new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED, "PROJ资格事实损坏");
        }
        if (scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)
                || scope.placeholderProjectIds() != null && scope.placeholderProjectIds().contains(projectId)) {
            throw new CommerceDeliveryScopeCommandException(PROJECT_DATA_SCOPE_DENIED,
                    "项目不在当前ACTION_EDIT范围");
        }
        Snapshot current = new Snapshot(TenantContext.require(), projectId, actorId, participant.lifecycleStatus(),
                participant.currentStage(), participant.projectVersion(), participant.factVersion(), scope.treeVersion());
        if (expected != null && (!Objects.equals(expected.projectVersion(), current.projectVersion())
                || !Objects.equals(expected.participantFactVersion(), current.participantFactVersion())
                || !Objects.equals(expected.treeVersion(), current.treeVersion()))) {
            throw new CommerceDeliveryScopeCommandException(PROJECT_FACT_STALE, "项目资格事实已变化");
        }
        return current;
    }

    private void requireIdentity(Long tenantId, Long projectId, Long actorId) {
        if (!Objects.equals(TenantContext.require(), tenantId) || tenantId <= 0 || projectId == null || projectId <= 0
                || actorId == null || actorId <= 0) {
            throw new CommerceDeliveryScopeCommandException(TENANT_CONTEXT_MISMATCH, "受信租户或主体非法");
        }
    }

    public record Snapshot(Long tenantId, Long projectId, Long managerUserId, String lifecycleStatus,
                           String currentStage, Integer projectVersion, Long participantFactVersion,
                           Long treeVersion) {

        public boolean protectsReduction() {
            return !"ACTIVE".equals(lifecycleStatus) || "S5".equals(currentStage) || "S6".equals(currentStage);
        }
    }

    private static final class TenantContext {
        private static Long require() {
            try {
                return cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
            } catch (RuntimeException exception) {
                throw new CommerceDeliveryScopeCommandException(TENANT_CONTEXT_MISMATCH, "缺少受信租户上下文");
            }
        }
    }
}
