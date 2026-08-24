package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.ProjectProgressResult;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;

@Service
@RequiredArgsConstructor
public class ProjectProgressQueryService {
    private final ProjectProgressSnapshotService snapshotService;
    private final ProjectTreeScopeService scopeService;

    public ProjectProgressResult getCurrent(Long projectId, ProjectProgressPolicyService.Actor actor) {
        ProjectProgressResult result = snapshotService.calculate(projectId, actor);
        ProjectTreeScopeService.ProjectTreeScope scope = scopeService.resolve(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ACTION_VIEW, result.treeVersion()));
        List<ProjectProgressResult.Item> visibleItems = result.items().stream()
                .filter(item -> scope.visibility(item.childProjectId()) == ProjectTreeScopeService.Visibility.FULL)
                .toList();
        Projection projection = projectVisibleProgress(visibleItems);
        return new ProjectProgressResult(result.projectId(), result.policyRevisionId(), result.treeVersion(),
                result.sourceWatermark(), projection.status(), projection.progress(), visibleItems);
    }

    private Projection projectVisibleProgress(List<ProjectProgressResult.Item> items) {
        if (items.isEmpty() || items.stream().anyMatch(item -> item.missingReason() != null
                || item.childProgress() == null || item.normalizedWeight() == null)) {
            return new Projection("PENDING", null);
        }
        BigDecimal weight = items.stream().map(ProjectProgressResult.Item::normalizedWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weight.signum() <= 0) {
            return new Projection("PENDING", null);
        }
        BigDecimal weightedProgress = items.stream()
                .map(item -> item.childProgress().multiply(item.normalizedWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(weight, 4, RoundingMode.HALF_UP);
        return new Projection("READY", weightedProgress);
    }

    private record Projection(String status, BigDecimal progress) {
    }
}
