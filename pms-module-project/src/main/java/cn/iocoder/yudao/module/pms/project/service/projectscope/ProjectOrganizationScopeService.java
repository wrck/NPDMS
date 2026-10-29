package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi.Organization;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.OrganizationProjectScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** SYSTEM owns effective organization rows; PROJ matches only its own project organization. */
@Service
@RequiredArgsConstructor
public class ProjectOrganizationScopeService {
    private final OrganizationScopeApi organizations;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    private final ProjectMasterMapper projects;

    public record Query(Long tenantId, Long userId, String actionCode, boolean summaryOnly, Set<Long> candidateProjectIds) { }

    public List<Organization> rows(Query query) {
        if (!Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()))
            throw new IllegalArgumentException("PROJECT_ORGANIZATION_TENANT_MISMATCH");
        var roleCodes = query.summaryOnly() ? Set.of(ProjectMemberRoles.PROJECT_MANAGER) : ProjectMemberRoles.SERVICE_CODES;
        if (!permissions.hasAnyRoles(query.userId(), roleCodes.toArray(String[]::new))) return List.of();
        var user = users.getUser(query.userId());
        if (user == null || !Objects.equals(user.getId(), query.userId()) || !Integer.valueOf(0).equals(user.getStatus()))
            return List.of();
        return organizations.getActiveScopes(query.userId()).stream()
                .filter(scope -> Objects.equals(scope.getUserId(), query.userId()))
                .filter(scope -> roleCodes.contains(scope.getScopeRole()))
                .filter(scope -> scope.getCompanyId() != null && scope.getCompanyId() > 0)
                .filter(scope -> scope.getDepartmentId() == null || scope.getDepartmentId() > 0)
                .map(scope -> new Organization(scope.getCompanyId(), scope.getDepartmentId())).distinct().toList();
    }

    public Set<Long> projectIds(Query query) {
        var rows = rows(query);
        if (rows.isEmpty() || query.candidateProjectIds() != null && query.candidateProjectIds().isEmpty()) return Set.of();
        return Set.copyOf(projects.selectOrganizationProjectIds(new OrganizationProjectScopeQuery(
                query.tenantId(), rows, query.candidateProjectIds(), !"PROJECT_VIEW".equals(query.actionCode()))));
    }
}
