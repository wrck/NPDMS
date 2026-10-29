package cn.iocoder.yudao.module.pms.project.api.scope;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectScopeApiImpl implements ProjectScopeApi, ProjectViewReadScopeApi {

    private final ProjectTreeScopeService scopeService;

    @Override
    public ReadScope prepare(ProjectAllScopeQuery query) {
        return scopeService.prepareViewReadScope(query);
    }

    @Override
    public ProjectScopeResult resolve(ProjectScopeQuery query) {
        ProjectTreeScopeService.ProjectTreeScope scope = scopeService.resolve(query);
        return new ProjectScopeResult(scope.rootProjectId(), scope.treeVersion(),
                scope.fullProjectIds(), scope.placeholderProjectIds());
    }

    @Override
    public ProjectScopeResult resolveCurrent(ProjectCurrentScopeQuery query) {
        ProjectTreeScopeService.ProjectTreeScope scope = scopeService.resolveCurrent(query);
        return new ProjectScopeResult(scope.rootProjectId(), scope.treeVersion(),
                scope.fullProjectIds(), scope.placeholderProjectIds());
    }

    @Override
    public Set<Long> resolveAllCurrent(ProjectAllScopeQuery query) {
        return cn.iocoder.yudao.framework.common.util.authorization.AuthorizationReadScope.read(
                new AllScopeReadKey(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId(),
                        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.isIgnore(), query,
                        cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectReadContext.current()),
                () -> scopeService.resolveAllFullProjectIds(
                        query.tenantId(), query.subjectUserId(), query.actionCode()));
    }

    private record AllScopeReadKey(Long contextTenantId, boolean tenantIgnored, ProjectAllScopeQuery query,
            cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectReadContext.Scope officeReadScope) {}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectScopeResult lockAndRevalidate(ProjectScopeRevalidationQuery query) {
        ProjectTreeScopeService.ProjectTreeScope scope = scopeService.lockAndRevalidate(query);
        return new ProjectScopeResult(scope.rootProjectId(), scope.treeVersion(),
                scope.fullProjectIds(), scope.placeholderProjectIds());
    }
}
