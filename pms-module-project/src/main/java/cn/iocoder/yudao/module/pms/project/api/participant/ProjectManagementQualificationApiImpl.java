package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectCurrentStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectManagementQualificationApiImpl implements ProjectManagementQualificationApi {
    private final ProjectParticipantFactApi participants;
    private final ProjectRegionalManagerFactApi regionalManagers;
    private final ProjectMasterMapper projects;
    private final ProjectCurrentStageService currentStages;

    @Override
    public Fact inspect(Query query) {
        validate(query);
        ProjectParticipantFact member;
        try {
            member = participants.inspectAuthorization(new ProjectParticipantFactQuery(query.projectId(), query.userId(),
                    ProjectMemberRoles.MANAGEMENT_CODES, LocalDateTime.now()));
        } catch (ServiceException denied) {
            if (denied.getCode() != ProjectParticipantFactApi.SCOPE_DENIED_ERROR_CODE) throw denied;
            member = null;
        }
        if (member != null) {
            if (!Objects.equals(member.projectId(), query.projectId()) || !Objects.equals(member.userId(), query.userId())
                    || managementRoles(member).isEmpty()) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
            if (!"ACTIVE".equals(member.lifecycleStatus())) return null;
            return new Fact(query, Source.PROJECT_MEMBER, member, null);
        }
        try {
            var regional = regionalManagers.inspect(new ProjectRegionalManagerFactApi.Query(query.tenantId(), query.projectId(), query.userId()));
            return regional == null ? null : new Fact(query, Source.REGIONAL_MANAGER, null, regional);
        } catch (ServiceException denied) {
            if (!PROJECT_TREE_SCOPE_FORBIDDEN.getCode().equals(denied.getCode())) throw denied;
            return null;
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Fact lockCurrent(CurrentRevalidation query) {
        if (query == null || query.expectedProjectVersion() == null || query.expectedProjectVersion() < 0
                || !"ACTIVE".equals(query.requiredLifecycleStatus())
                || query.requiredCurrentStage() != null && !query.requiredCurrentStage().matches("S[0-6]"))
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var observed = inspect(query.subject());
        return observed == null ? null : lockAndRevalidate(new Revalidation(observed, query.expectedProjectVersion(),
                query.requiredLifecycleStatus(), query.requiredCurrentStage()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Fact lockAndRevalidate(Revalidation query) {
        if (query == null || query.expected() == null || query.expected().source() == null
                || query.expectedProjectVersion() == null || !"ACTIVE".equals(query.requiredLifecycleStatus())
                || query.requiredCurrentStage() != null && !query.requiredCurrentStage().matches("S[0-6]"))
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var expected = query.expected();
        validate(expected.subject());
        if (!Objects.equals(query.expectedProjectVersion(), expected.projectVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        if (expected.source() == Source.PROJECT_MEMBER) {
            if (expected.member() == null || expected.regional() != null) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
            var current = participants.lockAndRevalidateAuthorization(new ProjectParticipantFactRevalidationQuery(
                    expected.subject().projectId(), expected.subject().userId(), query.expectedProjectVersion(),
                    query.requiredLifecycleStatus(), query.requiredCurrentStage(), managementRoles(expected.member())));
            if (!Objects.equals(expected.member(), current)) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
            return new Fact(expected.subject(), Source.PROJECT_MEMBER, current, null);
        }
        if (expected.regional() == null || expected.member() != null) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var current = regionalManagers.lockAndRevalidate(new ProjectRegionalManagerFactApi.RevalidationQuery(
                new ProjectRegionalManagerFactApi.Query(expected.subject().tenantId(), expected.subject().projectId(), expected.subject().userId()),
                query.expectedProjectVersion()));
        if (!Objects.equals(expected.regional(), current)) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var project = projects.selectByIdForUpdate(expected.subject().projectId());
        if (project == null || !Objects.equals(project.getTenantId(), expected.subject().tenantId())
                || !Objects.equals(project.getVersion(), query.expectedProjectVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        if (!Objects.equals(project.getLifecycleStatus(), query.requiredLifecycleStatus())
                || query.requiredCurrentStage() != null && !(project.getActivePlanVersionId() == null
                    ? Objects.equals(project.getCurrentStage(), query.requiredCurrentStage())
                    : currentStages.isActive(project, query.requiredCurrentStage()))) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        return new Fact(expected.subject(), Source.REGIONAL_MANAGER, null, current);
    }

    private static Set<String> managementRoles(ProjectParticipantFact member) {
        return member.effectiveRoleCodes().stream().filter(ProjectMemberRoles.MANAGEMENT_CODES::contains).collect(Collectors.toUnmodifiableSet());
    }

    private static void validate(Query query) {
        if (query == null || query.tenantId() == null || query.projectId() == null || query.projectId() <= 0
                || query.userId() == null || query.userId() <= 0 || TenantContextHolder.isIgnore()
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || !Objects.equals(query.userId(), SecurityFrameworkUtils.getLoginUserId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
    }
}
