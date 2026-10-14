package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class ProjectBusinessContextApiImpl implements ProjectBusinessContextApi {
    private final ProjectMasterMapper projects;
    private final ProjectScopeApi scopes;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper members;

    @Override
    public ServiceManagers serviceManagers(Query query) {
        var context = inspect(query);
        var rows = members.selectParticipantFacts(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery.builder()
                .tenantId(query.tenantId())
                .projectId(query.projectId())
                .subjectUserId(null)
                .requiredRoleCodes(cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES)
                .effectiveAt(java.time.LocalDateTime.now()).build());
        return new ServiceManagers(context.projectVersion(), rows.stream().map(value -> value.getUserId()).distinct().sorted().toList());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ServiceManagers lockServiceManagers(Revalidation expected) {
        var context = lockAndRevalidate(expected);
        var query = expected.query();
        var candidates = members.selectParticipantFacts(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery.builder()
                .tenantId(query.tenantId())
                .projectId(query.projectId())
                .subjectUserId(null)
                .requiredRoleCodes(cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES)
                .effectiveAt(java.time.LocalDateTime.now()).build()).stream().map(value -> value.getUserId()).distinct().sorted().toList();
        var users = new java.util.ArrayList<Long>();
        for (Long user : candidates) {
            var rows = members.selectParticipantFactsForUpdate(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLockQuery.builder()
                .tenantId(query.tenantId())
                .projectId(query.projectId())
                .userId(user)
                .requiredRoleCodes(cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES).build());
            if (!rows.isEmpty()) users.add(user);
        }
        return new ServiceManagers(context.projectVersion(), java.util.List.copyOf(users));
    }

    @Override
    public Context inspect(Query query) {
        validate(query);
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(query.actorId())
                .anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        requireScope(query, scope);
        return context(query, projects.selectById(query.projectId()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Context lockAndRevalidate(Revalidation expected) {
        if (expected == null || expected.expectedProjectVersion() == null) throw exception(PROJECT_VERSION_CONFLICT);
        var query = expected.query();
        validate(query);
        var scope = scopes.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(query.actorId())
                .anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW)
                .expectedScopeVersion(query.expectedScopeVersion()).build());
        requireScope(query, scope);
        var current = context(query, projects.selectByIdForUpdate(query.projectId()));
        if (!Objects.equals(current.projectVersion(), expected.expectedProjectVersion())) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        return current;
    }

    private static void validate(Query query) {
        if (query == null || query.tenantId() == null || query.actorId() == null || query.actorId() <= 0
                || query.projectId() == null || query.projectId() <= 0 || query.expectedScopeVersion() == null
                || query.expectedScopeVersion() < 0 || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private static void requireScope(Query query, ProjectScopeResult scope) {
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        if (!Objects.equals(scope.treeVersion(), query.expectedScopeVersion())) throw exception(PROJECT_TREE_VERSION_CONFLICT);
    }

    private static Context context(Query query, ProjectMasterDO project) {
        if (project == null || !Objects.equals(project.getTenantId(), query.tenantId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        return Context.builder()
                .tenantId(query.tenantId())
                .projectId(project.getId())
                .projectVersion(project.getVersion())
                .scopeVersion(query.expectedScopeVersion())
                .projectCode(project.getProjectCode())
                .projectName(project.getProjectName())
                .customerId(project.getCustomerId())
                .customerCode(project.getCustomerCode())
                .customerName(project.getCustomerName())
                .departmentId(project.getDepartmentId())
                .departmentCode(project.getDepartmentCode())
                .departmentName(project.getDepartmentName()).build();
    }
}
