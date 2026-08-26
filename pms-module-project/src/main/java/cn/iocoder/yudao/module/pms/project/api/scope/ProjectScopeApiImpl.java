package cn.iocoder.yudao.module.pms.project.api.scope;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectScopeApiImpl implements ProjectScopeApi {

    private final ProjectTreeScopeService scopeService;

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
}
