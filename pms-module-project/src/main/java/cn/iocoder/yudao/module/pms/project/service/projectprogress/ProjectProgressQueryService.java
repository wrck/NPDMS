package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.ProjectProgressResult;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectProgressQueryService {
    private final ProjectProgressSnapshotService snapshotService;
    private final ProjectTreeScopeService scopeService;

    public ProjectProgressResult getCurrent(Long projectId, ProjectProgressPolicyService.Actor actor) {
        ProjectProgressResult result = snapshotService.calculate(projectId, actor);
        ProjectTreeScopeService.ProjectTreeScope scope = scopeService.resolve(
                actor.actorId(), projectId, result.treeVersion());
        return new ProjectProgressResult(result.projectId(), result.policyRevisionId(), result.treeVersion(),
                result.sourceWatermark(), result.status(), result.progress(), result.items().stream()
                .filter(item -> scope.visibility(item.childProjectId()) == ProjectTreeScopeService.Visibility.FULL)
                .toList());
    }
}
