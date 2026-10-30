package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationRoleScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

/** Reuses the regional source of duration qualification without creating or impersonating a PM membership. */
@Service
@RequiredArgsConstructor
public class ProjectRegionalManagerFactApiImpl implements ProjectRegionalManagerFactApi {
    private final ProjectMasterMapper projects;
    private final OrganizationScopeApi organizations;

    @Override public Fact inspect(Query query) {
        validate(query);
        var project = projects.selectById(query.projectId());
        requireProject(project, query);
        if (organizations.inspectRoleScope(scopeQuery(project, query)) == null)
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        return fact(project, query);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Fact lockAndRevalidate(RevalidationQuery query) {
        if (query == null || query.expectedProjectVersion() == null || query.expectedProjectVersion() < 0)
            throw exception(PROJECT_VERSION_CONFLICT);
        validate(query.subject());
        var project = projects.selectByIdForUpdate(query.subject().projectId());
        requireProject(project, query.subject());
        if (!Objects.equals(project.getVersion(), query.expectedProjectVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        // SYSTEM performs an uncached current locking read of the scope, account, role and role grant.
        if (organizations.lockRoleScope(scopeQuery(project, query.subject())) == null)
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        return fact(project, query.subject());
    }

    private static OrganizationRoleScopeQuery scopeQuery(ProjectMasterDO project, Query query) {
        return new OrganizationRoleScopeQuery(query.tenantId(), query.userId(), project.getCompanyId(),
                project.getDepartmentId(), ProjectMemberRoles.SERVICE_CODES);
    }
    private static Fact fact(ProjectMasterDO project, Query query) {
        return new Fact(project.getId(), query.userId(), project.getVersion());
    }
    private static void validate(Query query) {
        if (query == null || query.tenantId() == null || query.projectId() == null || query.projectId() <= 0
                || query.userId() == null || query.userId() <= 0
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore()
                || !Objects.equals(query.userId(), SecurityFrameworkUtils.getLoginUserId()))
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
    }
    private static void requireProject(ProjectMasterDO project, Query query) {
        if (project == null || !Objects.equals(project.getId(), query.projectId())
                || !Objects.equals(project.getTenantId(), query.tenantId()) || !"ACTIVE".equals(project.getLifecycleStatus())
                || project.getVersion() == null || project.getVersion() < 0
                || project.getCompanyId() == null || project.getCompanyId() <= 0
                || project.getDepartmentId() != null && project.getDepartmentId() <= 0)
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
    }
}
