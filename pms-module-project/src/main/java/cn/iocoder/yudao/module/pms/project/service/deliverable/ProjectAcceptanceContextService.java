package cn.iocoder.yudao.module.pms.project.service.deliverable;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class ProjectAcceptanceContextService implements ProjectAcceptanceContextApi {
    private final ProjectMasterMapper projects;
    private final ProjectScopeApi scopes;

    @Override public Context inspect(Query query) {
        if (query == null) throw new IllegalArgumentException("ACCEPTANCE_PROJECT_CONTEXT_REQUIRED");
        var project = require(query, projects.selectById(query.projectId()));
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(), query.actorId(), query.projectId(), ProjectScopeApi.ACTION_EDIT));
        return context(query, project, scope);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Context lock(Query query, Long expectedProjectVersion, Long expectedTreeVersion) {
        if (query == null || expectedProjectVersion == null || expectedTreeVersion == null) throw new IllegalArgumentException("ACCEPTANCE_PROJECT_CONTEXT_REQUIRED");
        var scope = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(query.tenantId(), query.actorId(), query.projectId(), ProjectScopeApi.ACTION_EDIT, expectedTreeVersion));
        var project = require(query, projects.selectByIdForUpdate(query.projectId()));
        var result = context(query, project, scope);
        if (!Objects.equals(project.getVersion(), expectedProjectVersion) || !Objects.equals(scope.treeVersion(), expectedTreeVersion))
            throw new IllegalArgumentException("ACCEPTANCE_PROJECT_CONTEXT_CHANGED");
        return result;
    }
    private Context context(Query query, ProjectMasterDO project, ProjectScopeResult scope) {
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(query.projectId()))
            throw new IllegalArgumentException("ACCEPTANCE_PROJECT_SCOPE_FORBIDDEN");
        return new Context(project.getId(), scope.rootProjectId(), project.getVersion(), scope.treeVersion(), project.getLifecycleStatus());
    }
    private ProjectMasterDO require(Query query, ProjectMasterDO project) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()) || query.actorId() == null
                || project == null || !Objects.equals(project.getTenantId(), query.tenantId()) || Boolean.TRUE.equals(project.getDeleted()))
            throw new IllegalArgumentException("ACCEPTANCE_PROJECT_UNAVAILABLE");
        return project;
    }
}
