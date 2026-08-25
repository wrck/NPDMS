package cn.iocoder.yudao.module.pms.project.service.projectclosureguard;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;

@Service
@RequiredArgsConstructor
public class ProjectClosureGuardService {
    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectTreePathMapper pathMapper;
    private final ProjectProgressSnapshotMapper snapshotMapper;
    private final ProjectTreeScopeService scopeService;
    private final ClosureStatePort closureStatePort;
    private final OperationAuditApi auditService;

    @Transactional(rollbackFor = Exception.class)
    public ProjectClosureGuardResult evaluate(Long projectId, long expectedTreeVersion, Actor actor) {
        validate(projectId, expectedTreeVersion, actor);
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        Long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        ProjectMasterDO rootLock = projectMapper.selectByIdForUpdate(rootId);
        if (rootLock == null || !Objects.equals(rootLock.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        ProjectTreeVersionDO active = treeVersionMapper.selectLatestActive(rootId);
        if (active == null || !Objects.equals(active.getTreeVersion(), expectedTreeVersion)) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        ProjectTreeScopeService.ProjectTreeScope scope = scopeService.resolve(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ACTION_VIEW, expectedTreeVersion));
        if (scope.visibility(projectId) != ProjectTreeScopeService.Visibility.FULL) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }

        List<Long> descendantIds = pathMapper.selectByAncestor(rootId, expectedTreeVersion, projectId, null).stream()
                .filter(path -> path.getDistance() != null && path.getDistance() > 0)
                .map(ProjectTreePathDO::getDescendantProjectId)
                .distinct().toList();
        Map<Long, ProjectMasterDO> projects = descendantIds.isEmpty() ? Map.of()
                : projectMapper.selectBatchIds(descendantIds).stream()
                    .filter(candidate -> Objects.equals(candidate.getTenantId(), actor.tenantId()))
                    .collect(Collectors.toMap(ProjectMasterDO::getId, Function.identity()));
        Map<Long, ClosureStatePort.ClosureState> states = closureStatePort.findByProjectIds(
                actor.tenantId(), descendantIds);
        List<ProjectClosureGuardResult.BlockingProject> blockers = new ArrayList<>();
        for (Long descendantId : descendantIds) {
            ClosureStatePort.ClosureState state = states.getOrDefault(
                    descendantId, ClosureStatePort.ClosureState.EXECUTING);
            if (state == ClosureStatePort.ClosureState.CLOSED) continue;
            ProjectMasterDO descendant = projects.get(descendantId);
            boolean full = scope.visibility(descendantId) == ProjectTreeScopeService.Visibility.FULL;
            blockers.add(new ProjectClosureGuardResult.BlockingProject(descendantId,
                    full && descendant != null ? descendant.getProjectCode() : null,
                    full && descendant != null ? descendant.getProjectName() : null, state.name()));
        }

        List<Long> pendingProgressProjects = pendingProgressProjects(
                projectId, rootId, expectedTreeVersion, descendantIds, actor.tenantId());
        boolean allowed = blockers.isEmpty() && pendingProgressProjects.isEmpty();
        auditService.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "PROJECT_CLOSURE_GUARD_EVALUATE", "ProjectClosureGuard", String.valueOf(projectId),
                allowed ? "PASS" : "REJECTED", Map.of("treeVersion", expectedTreeVersion,
                        "descendantCount", descendantIds.size(), "blockerCount", blockers.size(),
                        "pendingProgressCount", pendingProgressProjects.size()));
        return new ProjectClosureGuardResult(allowed, expectedTreeVersion, List.copyOf(blockers),
                List.copyOf(pendingProgressProjects));
    }

    private List<Long> pendingProgressProjects(Long projectId, Long rootId, long treeVersion,
                                               List<Long> descendantIds, Long tenantId) {
        Set<Long> candidates = new LinkedHashSet<>();
        candidates.add(projectId);
        candidates.addAll(descendantIds);
        Set<Long> aggregateProjects = pathMapper.selectParentsWithChildren(rootId, treeVersion, candidates);
        if (aggregateProjects.isEmpty()) return List.of();
        Map<Long, ProjectProgressSnapshotDO> snapshots = snapshotMapper.selectLatestByProjects(
                tenantId, aggregateProjects).stream().collect(Collectors.toMap(
                ProjectProgressSnapshotDO::getProjectId, Function.identity()));
        return aggregateProjects.stream().filter(candidate -> {
            ProjectProgressSnapshotDO snapshot = snapshots.get(candidate);
            return snapshot == null || !"READY".equals(snapshot.getSnapshotStatus());
        }).sorted().toList();
    }

    private void validate(Long projectId, long expectedTreeVersion, Actor actor) {
        if (projectId == null || expectedTreeVersion <= 0 || actor == null || actor.tenantId() == null
                || actor.actorId() == null || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {}
}
