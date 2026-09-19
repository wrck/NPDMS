package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessConfigurationApi;
import org.springframework.beans.factory.ObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectVerifiedOperationScope;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** SOL retains authorization and execution-state ownership for all public capability callbacks. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisAccess {
    private final RequirementAnalysisMapper mapper;
    private final ProjectScopeApi scopes;
    private final ProjectParticipantFactApi participants;
    private final PermissionApi permissions;
    private final ProjectWorkBindingFactApi bindings;
    private final RequirementAnalysisExecutionAccess executions;
    private final ObjectProvider<ProjectBusinessConfigurationApi> configurations;

    public RequirementAnalysisRevisionDO read(Long revisionId, EntityActor actor) {
        var row = mapper.selectRevision(new RequirementRevisionQuery(actor.tenantId(), revisionId));
        if (row == null) throw exception(REQUIREMENT_STATUS_INVALID);
        requireRead(row.getProjectId(), actor, "DRAFT".equals(row.getRevisionState()));
        return row;
    }

    public void requireRead(Long projectId, EntityActor actor, boolean draft) {
        if (!permissions.hasAnyPermissions(actor.userId(), "pms:requirement-analysis:query", "pms:requirement-analysis:manage")) throw exception(FORBIDDEN);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(), projectId, ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
        if (draft && !isManager(projectId, actor)) throw exception(FORBIDDEN);
    }

    public boolean isManager(Long projectId, EntityActor actor) {
        try {
            if (!permissions.hasAnyPermissions(actor.userId(), "pms:requirement-analysis:manage")) return false;
            var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(), projectId, ProjectScopeApi.ACTION_MANAGE));
            var participant = RequirementAnalysisManagerFacts.inspect(participants, permissions, projectId, actor.userId());
            return scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(projectId)
                    && participant != null && "ACTIVE".equals(participant.lifecycleStatus())
                    && participant.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
        } catch (RuntimeException unavailable) {
            // Preserve the original query behavior: unavailable manager facts disable editing only.
            return false;
        }
    }

    /** Original authorization and project-first lock order, retained by this business Owner. */
    public void lockScope(Long projectId, EntityActor actor) {
        if (!permissions.hasAnyPermissions(actor.userId(), "pms:requirement-analysis:manage")) throw exception(FORBIDDEN);
        var observed = scopes.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(), projectId, ProjectScopeApi.ACTION_MANAGE));
        if (observed == null || observed.treeVersion() == null) throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        var locked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(), actor.userId(), projectId,
                ProjectScopeApi.ACTION_MANAGE, observed.treeVersion()));
        if (locked.fullProjectIds() == null || !locked.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
        var manager = RequirementAnalysisManagerFacts.inspect(participants, permissions, projectId, actor.userId());
        if (manager == null) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        participants.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(projectId, manager.userId(), manager.projectVersion(),
                "ACTIVE", null, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
    }

    public RequirementAnalysisExecutionAccess.Frozen lockExecution(Long projectId, String snapshot,
                                                                   ProjectBusinessExecutionSelection selection) {
        return lockExecution(projectId, snapshot, selection, null, null);
    }

    public RequirementAnalysisExecutionAccess.Frozen lockExecution(Long projectId, String snapshot,
            ProjectBusinessExecutionSelection selection, EntityActor actor, Long targetRevisionId) {
        var frame = ProjectVerifiedOperationScope.current();
        if (actor == null && frame != null && "SOL".equals(frame.ownerContext())
                && "REQUIREMENT_ANALYSIS".equals(frame.objectType()))
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        if (actor != null) selection = ProjectOwnerOperationScope.executionFor(actor.tenantId(), actor.userId(), projectId,
                "SOL", "REQUIREMENT_ANALYSIS", targetRevisionId == null ? null : targetRevisionId.toString(), selection);
        // Owner authorization and row concurrency are enforced by the caller; the source round is only provenance.
        if (selection == null && snapshot != null) {
            var frozen = executions.frozen(projectId, snapshot);
            if (frozen.configuration() != null) {
                if (actor == null) throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
                RequirementAnalysisConfiguration.require(frozen.configuration(), actor.tenantId(), projectId);
            }
            return frozen;
        }
        if (selection == null && actor != null) return initialConfiguration(projectId, actor);
        ProjectWorkBindingFact binding;
        if (selection != null) {
            binding = executions.lockRequested(projectId, selection);
            if (snapshot != null) binding = executions.currentBinding(projectId, snapshot, selection);
        } else {
            binding = snapshot == null ? bindings.inspect(new ProjectWorkBindingFactQuery(projectId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS))
                    : executions.currentBinding(projectId, snapshot, null);
        }
        if (binding == null) throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        var lockedBinding = executions.lockBinding(binding);
        return actor == null ? executions.lockCurrent(lockedBinding)
                : executions.lockCurrent(lockedBinding, actor, targetRevisionId);
    }

    /** Read-only initial configuration. The writer separately retains Owner scope and manager locks. */
    public RequirementAnalysisExecutionAccess.Frozen initialConfiguration(Long projectId, EntityActor actor) {
        requireRead(projectId, actor, false);
        var configuration = configurations.getObject().resolve(new ProjectBusinessConfigurationApi.Query(
                actor.tenantId(), actor.userId(), projectId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
        RequirementAnalysisConfiguration.require(configuration, actor.tenantId(), projectId);
        return new RequirementAnalysisExecutionAccess.Frozen(null, null, null, configuration);
    }

    public RequirementAnalysisRevisionDO lock(Long revisionId, Integer expectedVersion, EntityActor actor,
                                               ProjectBusinessExecutionSelection selection, boolean draftOnly) {
        var observed = mapper.selectRevision(new RequirementRevisionQuery(actor.tenantId(), revisionId));
        if (observed == null) throw exception(REQUIREMENT_STATUS_INVALID);
        lockScope(observed.getProjectId(), actor);
        lockExecution(observed.getProjectId(), observed.getExecutionSnapshot(), selection, actor, revisionId);
        var locked = mapper.lockRevision(new RequirementRevisionQuery(actor.tenantId(), revisionId));
        if (locked == null || !Objects.equals(expectedVersion, locked.getVersion())
                || draftOnly && !"DRAFT".equals(locked.getRevisionState())) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        return locked;
    }
}
