package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.normalclosure.NormalClosureApprovalFactApi;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureExitApi.ClosureExitCommand;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationRoleScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

/** Separate callback path: no login replacement and no relaxation of ordinary self-service APIs. */
@Component
@RequiredArgsConstructor
public class NormalClosureApprovedAccess {
    private final NormalClosureAccess access;
    private final NormalClosureApprovalFactApi approvals;
    private final ProjectScopeApi scopes;
    private final ProjectParticipantFactApi participants;
    private final OrganizationScopeApi organizations;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public NormalClosureAccess.Context lock(ClosureExitCommand command) {
        if (command == null || command.tenantId() == null || TenantContextHolder.isIgnore()
                || !Objects.equals(command.tenantId(), TenantContextHolder.getTenantId())
                || command.expectedReviewerUserId() == null
                || !Objects.equals(command.expectedReviewerUserId(), SecurityFrameworkUtils.getLoginUserId())
                || command.actorId() == null || command.actorId() <= 0
                || command.processInstanceId() == null || command.processInstanceId().isBlank()
                || !("BPM_NORMAL_CLOSURE:" + command.processInstanceId()).equals(command.correlationId()))
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var project = access.lockProject(command.projectId(), command.tenantId());
        if (!Objects.equals(project.getVersion(), command.expectedProjectVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        if (!"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        // Project locks precede ACC application locks. Only ACC interprets frozen candidates/BPM evidence.
        var approval = approvals.requireApprovedApplicant(new NormalClosureApprovalFactApi.Query(command.tenantId(),
                command.projectId(), command.applicationId(), command.processInstanceId(), command.actorId(),
                command.expectedReviewerUserId(), command.expectedProjectVersion(), command.expectedTreeVersion()));
        if (approval == null || !Objects.equals(approval.snapshotId(), command.snapshotId())
                || !Objects.equals(approval.applicationVersion(), command.sourceRecordRevision())
                || !Objects.equals(approval.fromStage(), command.expectedFromStage())
                || !Objects.equals(approval.serviceManagerUserId(), command.expectedServiceManagerUserId())
                || !Objects.equals(approval.sourceDigest(), command.expectedSourceDigest()))
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var scope = scopes.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(command.tenantId()).subjectUserId(command.actorId()).anchorProjectId(command.projectId())
                .actionCode(ProjectScopeApi.ACTION_MANAGE).expectedScopeVersion(command.expectedTreeVersion()).build());
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !Objects.equals(scope.treeVersion(), command.expectedTreeVersion())
                || !scope.fullProjectIds().contains(command.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        ProjectParticipantFact observed;
        try {
            observed = participants.inspectAuthorization(new ProjectParticipantFactQuery(command.projectId(), command.actorId(),
                    ProjectMemberRoles.MANAGEMENT_CODES, java.time.LocalDateTime.now()));
        } catch (ServiceException denied) {
            if (denied.getCode() != ProjectParticipantFactApi.SCOPE_DENIED_ERROR_CODE) throw denied;
            observed = null;
        }
        if (observed != null) {
            var member = participants.lockAndRevalidateAuthorization(new ProjectParticipantFactRevalidationQuery(
                    command.projectId(), command.actorId(), command.expectedProjectVersion(), "ACTIVE", null,
                    observed.effectiveRoleCodes().stream().filter(ProjectMemberRoles.MANAGEMENT_CODES::contains)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet())));
            if (!Objects.equals(observed, member) || member == null || !Objects.equals(member.userId(), command.actorId())
                    || !Objects.equals(member.projectId(), command.projectId())
                    || !Objects.equals(member.projectVersion(), command.expectedProjectVersion())
                    || member.effectiveRoleCodes().stream().noneMatch(ProjectMemberRoles.MANAGEMENT_CODES::contains))
                throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        } else {
            // Same regional qualification as the self-service path, using a current locked SYSTEM fact.
            if (organizations.lockSubjectRoleScope(new OrganizationRoleScopeQuery(command.tenantId(), command.actorId(),
                    project.getCompanyId(), project.getDepartmentId(), ProjectMemberRoles.SERVICE_CODES)) == null)
                throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return new NormalClosureAccess.Context(project, scope.treeVersion());
    }
}
