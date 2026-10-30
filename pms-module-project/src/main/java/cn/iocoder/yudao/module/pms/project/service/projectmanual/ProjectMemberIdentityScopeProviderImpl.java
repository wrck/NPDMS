package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectMemberFilterUsersQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.user.ProjectMemberIdentityScopeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service @RequiredArgsConstructor
public class ProjectMemberIdentityScopeProviderImpl implements ProjectMemberIdentityScopeProvider {
    private final ProjectTreeScopeService scopes;
    private final ProjectMemberAssignmentMapper members;
    private final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    @Override public Set<Long> resolveCurrent(Query query) {
        var actor = SecurityFrameworkUtils.getLoginUser();
        var tenant = TenantContextHolder.getTenantId();
        if (query == null || query.filter() == null || tenant == null || actor == null
                || !tenant.equals(query.tenantId()) || !tenant.equals(actor.getTenantId())
                || !java.util.Objects.equals(actor.getId(), query.actorId()) || query.actorId() == null || query.actorId() <= 0
                || !permissions.hasAnyPermissions(query.actorId(), "pms:project:query"))
            throw new SecurityException("PROJECT_MEMBER_FILTER_SCOPE_REQUIRED");
        if (cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectReadContext.current() != null) return Set.of();
        var scope = scopes.prepareViewReadScope(new ProjectAllScopeQuery(tenant, query.actorId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || !tenant.equals(scope.tenantId()) || !query.actorId().equals(scope.subjectUserId()))
            throw new SecurityException("PROJECT_MEMBER_FILTER_SCOPE_REQUIRED");
        if (scope.officeReadonlyProjectId() != null) return Set.of();
        if (scope.mode() != cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi.Mode.TENANT_ADMIN && scope.trees().isEmpty()) return Set.of();
        var roles = switch (query.filter()) {
            case MANAGER -> ProjectMemberRoles.MANAGEMENT_CODES;
            case SERVICE_MANAGER -> ProjectMemberRoles.SERVICE_CODES;
            case SALES -> ProjectMemberRoles.SALES_CODES;
        };
        return Set.copyOf(members.selectMemberFilterUserIds(new ProjectMemberFilterUsersQuery(
                tenant, roles, scope.effectiveAt(), new ProjectMemberFilterUsersQuery.Scope(scope))));
    }
}
