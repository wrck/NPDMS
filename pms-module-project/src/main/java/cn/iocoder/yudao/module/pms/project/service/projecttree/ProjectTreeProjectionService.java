package cn.iocoder.yudao.module.pms.project.service.projecttree;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeChangeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeChangeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.service.projecttree.command.MoveProjectSubtreeCommand;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_MOVE_CYCLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_MOVE_INVALID_PARENT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_PROJECTION_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_MANAGE;

@Service
@RequiredArgsConstructor
public class ProjectTreeProjectionService {
    public static final String MOVE_SCOPE = "POST:/pms/projects/{id}/actions/move";

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper versionMapper;
    private final ProjectTreePathMapper pathMapper;
    private final ProjectTreeChangeMapper changeMapper;
    private final PlatformCommandExecutionApi commandExecutionService;
    private final ProjectTreeMetrics metrics;
    private final ProjectTreeScopeService scopeService;

    public MoveProjectSubtreeResult move(MoveProjectSubtreeCommand command, Actor actor) {
        validateMove(command, actor);
        var execution = commandExecutionService.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), MOVE_SCOPE, actor.actorId(), command.idempotencyKey()),
                command.requestDigest(), MoveProjectSubtreeResult.class,
                () -> moveOnce(command, actor), result -> moveFacts(result, actor));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        MoveProjectSubtreeResult result = execution.response();
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new MoveProjectSubtreeResult(result.projectId(), result.parentId(), result.changeBatchId(),
                result.treeVersion(), result.affectedRoots(), true) : result;
    }

    private MoveProjectSubtreeResult moveOnce(MoveProjectSubtreeCommand command, Actor actor) {
        ProjectMasterDO initialNode = projectMapper.selectById(command.projectId());
        ProjectMasterDO initialParent = projectMapper.selectById(command.targetParentId());
        if (initialNode == null || !Objects.equals(initialNode.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        if (initialParent == null || !Objects.equals(initialParent.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_MOVE_INVALID_PARENT);
        }
        Long sourceRootId = rootId(initialNode);
        Long targetRootId = rootId(initialParent);
        List<Long> lockIds = java.util.stream.Stream.of(initialNode.getId(), initialParent.getId(),
                        sourceRootId, targetRootId).distinct().sorted().toList();
        Map<Long, ProjectMasterDO> locked = new HashMap<>();
        projectMapper.selectByIdsForUpdate(lockIds).forEach(project -> locked.put(project.getId(), project));
        ProjectMasterDO node = locked.get(initialNode.getId());
        ProjectMasterDO newParent = locked.get(initialParent.getId());
        if (node == null || newParent == null || !Objects.equals(node.getTenantId(), actor.tenantId())
                || !Objects.equals(newParent.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_MOVE_INVALID_PARENT);
        }
        sourceRootId = rootId(node);
        targetRootId = rootId(newParent);
        if (!lockIds.contains(sourceRootId) || !lockIds.contains(targetRootId)) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        ProjectTreeVersionDO sourceActive = requireStableActiveForUpdate(sourceRootId);
        ProjectTreeVersionDO targetActive = Objects.equals(sourceRootId, targetRootId)
                ? sourceActive : requireStableActiveForUpdate(targetRootId);
        if (!Objects.equals(sourceActive.getTreeVersion(), command.expectedTreeVersion())) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        scopeService.assertFullAccess(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), node.getId(), ACTION_MANAGE, sourceActive.getTreeVersion()));
        scopeService.assertFullAccess(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), newParent.getId(), ACTION_MANAGE, targetActive.getTreeVersion()));
        List<ProjectTreePathDO> subtreePaths = pathMapper.selectByAncestor(
                sourceRootId, sourceActive.getTreeVersion(), node.getId(), null);
        if (node.getId().equals(newParent.getId()) || subtreePaths.stream()
                .anyMatch(path -> path.getDescendantProjectId().equals(newParent.getId()))) {
            throw exception(PROJECT_MOVE_CYCLE);
        }
        List<Long> descendantIds = subtreePaths.stream().map(ProjectTreePathDO::getDescendantProjectId)
                .filter(id -> !id.equals(node.getId())).toList();
        List<ProjectMasterDO> descendants = descendantIds.isEmpty()
                ? List.of() : projectMapper.selectBatchIds(descendantIds);
        applyTruthMove(node, newParent, descendants, targetRootId);
        String changeBatchId = UUID.randomUUID().toString();
        long targetTreeVersion;
        List<AffectedRootVersion> affectedRoots;
        if (Objects.equals(sourceRootId, targetRootId)) {
            targetTreeVersion = sourceActive.getTreeVersion() + 1;
            publish(sourceRootId, targetTreeVersion, changeBatchId);
            affectedRoots = List.of(new AffectedRootVersion(sourceRootId, targetTreeVersion));
        } else {
            long sourceTreeVersion = sourceActive.getTreeVersion() + 1;
            publish(sourceRootId, sourceTreeVersion, changeBatchId);
            targetTreeVersion = targetActive.getTreeVersion() + 1;
            publish(targetRootId, targetTreeVersion, changeBatchId);
            affectedRoots = List.of(new AffectedRootVersion(sourceRootId, sourceTreeVersion),
                    new AffectedRootVersion(targetRootId, targetTreeVersion));
        }
        ProjectTreeChangeDO change = new ProjectTreeChangeDO();
        change.setChangeBatchId(changeBatchId);
        change.setOperationType("MOVE_SUBTREE");
        change.setProjectId(node.getId());
        change.setParentIdBefore(node.getParentId());
        change.setParentIdAfter(newParent.getId());
        change.setBaseTreeVersion(sourceActive.getTreeVersion());
        change.setNewTreeVersion(targetTreeVersion);
        change.setActorId(actor.actorId());
        change.setReason(command.reason());
        change.setOccurredAt(LocalDateTime.now());
        change.setVersion(0);
        if (changeMapper.insert(change) != 1) {
            throw new IllegalStateException("PROJECT_TREE_CHANGE_WRITE_FAILED");
        }
        return new MoveProjectSubtreeResult(node.getId(), newParent.getId(), changeBatchId,
                targetTreeVersion, affectedRoots, false);
    }

    private void applyTruthMove(ProjectMasterDO node, ProjectMasterDO newParent,
                                List<ProjectMasterDO> descendants, Long targetRootId) {
        String oldPrefix = childPrefix(node.getTreePath(), node.getId());
        String newTreePath = childPrefix(newParent.getTreePath(), newParent.getId());
        String newPrefix = childPrefix(newTreePath, node.getId());
        int oldDepth = node.getTreeDepth() == null ? 0 : node.getTreeDepth();
        int newDepth = (newParent.getTreeDepth() == null ? 0 : newParent.getTreeDepth()) + 1;
        int depthDelta = newDepth - oldDepth;
        ProjectMasterDO update = new ProjectMasterDO();
        update.setId(node.getId());
        update.setParentId(newParent.getId());
        update.setRootId(targetRootId);
        update.setTreePath(newTreePath);
        update.setTreeDepth(newDepth);
        update.setVersion(node.getVersion() == null ? 1 : node.getVersion() + 1);
        if (projectMapper.updateById(update) != 1) {
            throw new IllegalStateException("PROJECT_TREE_NODE_MOVE_FAILED");
        }
        for (ProjectMasterDO descendant : descendants) {
            if (descendant.getTreePath() == null || !descendant.getTreePath().startsWith(oldPrefix)) {
                throw new IllegalStateException("PROJECT_TREE_DESCENDANT_PATH_INVALID");
            }
            ProjectMasterDO descendantUpdate = new ProjectMasterDO();
            descendantUpdate.setId(descendant.getId());
            descendantUpdate.setRootId(targetRootId);
            descendantUpdate.setTreePath(newPrefix + descendant.getTreePath().substring(oldPrefix.length()));
            descendantUpdate.setTreeDepth((descendant.getTreeDepth() == null ? 0 : descendant.getTreeDepth()) + depthDelta);
            descendantUpdate.setVersion(descendant.getVersion() == null ? 1 : descendant.getVersion() + 1);
            if (projectMapper.updateById(descendantUpdate) != 1) {
                throw new IllegalStateException("PROJECT_TREE_DESCENDANT_MOVE_FAILED");
            }
        }
    }

    private ProjectTreeVersionDO requireStableActiveForUpdate(Long rootId) {
        ProjectTreeVersionDO active = versionMapper.selectLatestActiveForUpdate(rootId);
        ProjectTreeVersionDO latest = versionMapper.selectLatestForUpdate(rootId);
        if (active == null) throw exception(PROJECT_TREE_PROJECTION_UNAVAILABLE);
        if (latest != null && "BUILDING".equals(latest.getStatus())
                && latest.getTreeVersion() > active.getTreeVersion()) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        return active;
    }

    private PlatformCommandExecutionApi.SuccessFacts moveFacts(MoveProjectSubtreeResult result, Actor actor) {
        Map<String, Object> detail = Map.of("projectId", result.projectId(), "parentId", result.parentId(),
                "changeBatchId", result.changeBatchId(), "treeVersion", result.treeVersion(),
                "affectedRoots", result.affectedRoots());
        String payload = JsonUtils.toJsonString(detail);
        return new PlatformCommandExecutionApi.SuccessFacts("PROJECT_TREE_MOVE", "ProjectTree",
                String.valueOf(result.projectId()), actor.correlationId(), payload, "ProjectTreeChanged", payload);
    }

    private void validateMove(MoveProjectSubtreeCommand command, Actor actor) {
        if (command == null || command.projectId() == null || command.targetParentId() == null
                || command.expectedTreeVersion() == null || command.expectedTreeVersion() < 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || !command.requestDigest().matches("[0-9a-f]{64}")
                || actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("项目树移动命令不完整");
        }
    }

    private Long rootId(ProjectMasterDO project) {
        return project.getRootId() == null ? project.getId() : project.getRootId();
    }

    private String childPrefix(String treePath, Long projectId) {
        return (treePath == null ? "/" : treePath) + projectId + "/";
    }

    public ProjectionResult publish(Long rootProjectId, long treeVersion, String changeBatchId) {
        long started = System.nanoTime();
        ProjectMasterDO rootLock = projectMapper.selectByIdForUpdate(rootProjectId);
        if (rootLock == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        ProjectTreeVersionDO active = versionMapper.selectLatestActiveForUpdate(rootProjectId);
        long expectedVersion = active == null ? 1L : active.getTreeVersion() + 1;
        if (treeVersion != expectedVersion) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        ProjectTreeVersionDO version = buildingVersion(rootProjectId, treeVersion, changeBatchId);
        if (versionMapper.insert(version) != 1) {
            throw new IllegalStateException("PROJECT_TREE_VERSION_BUILD_START_FAILED");
        }
        int nodeCount = 0;
        try {
            List<ProjectMasterDO> nodes = projectMapper.selectTreeByRootId(rootProjectId);
            nodeCount = nodes.size();
            int pathCount = writeCompletePaths(rootProjectId, treeVersion, nodes);
            version.setStatus("ACTIVE");
            version.setNodeCount(nodeCount);
            version.setPathCount(pathCount);
            version.setActivatedAt(LocalDateTime.now());
            version.setFailedReason(null);
            if (versionMapper.updateById(version) != 1) {
                throw new IllegalStateException("PROJECT_TREE_VERSION_ACTIVATION_FAILED");
            }
            metrics.projection(true, System.nanoTime() - started, nodeCount);
            return new ProjectionResult(rootProjectId, treeVersion, nodeCount, pathCount);
        } catch (RuntimeException failure) {
            version.setStatus("FAILED");
            version.setFailedReason(failureReason(failure));
            versionMapper.updateById(version);
            metrics.projection(false, System.nanoTime() - started, nodeCount);
            throw failure;
        }
    }

    private int writeCompletePaths(Long rootId, long treeVersion, List<ProjectMasterDO> nodes) {
        Map<Long, ProjectMasterDO> byId = new HashMap<>();
        nodes.forEach(node -> byId.put(node.getId(), node));
        List<ProjectTreePathDO> paths = new java.util.ArrayList<>(1000);
        int pathCount = 0;
        for (ProjectMasterDO node : nodes) {
            ProjectMasterDO current = node;
            int distance = 0;
            Set<Long> visited = new HashSet<>();
            while (current != null) {
                if (!visited.add(current.getId())) {
                    throw new IllegalStateException("PROJECT_TREE_CYCLE");
                }
                ProjectTreePathDO path = new ProjectTreePathDO();
                path.setTreeVersion(treeVersion);
                path.setRootProjectId(rootId);
                path.setAncestorProjectId(current.getId());
                path.setDescendantProjectId(node.getId());
                path.setDistance(distance++);
                path.setVersion(0);
                paths.add(path);
                pathCount++;
                if (paths.size() == 1000) {
                    flushPaths(paths);
                }
                if (current.getParentId() == null) {
                    if (!Objects.equals(current.getId(), rootId)) {
                        throw new IllegalStateException("PROJECT_TREE_ROOT_INVALID");
                    }
                    current = null;
                } else {
                    current = byId.get(current.getParentId());
                    if (current == null) {
                        throw new IllegalStateException("PROJECT_TREE_ANCESTOR_MISSING");
                    }
                }
            }
        }
        flushPaths(paths);
        return pathCount;
    }

    private void flushPaths(List<ProjectTreePathDO> paths) {
        if (!paths.isEmpty() && !pathMapper.insertBatch(paths, 1000)) {
            throw new IllegalStateException("PROJECT_TREE_PATH_BATCH_INSERT_FAILED");
        }
        paths.clear();
    }

    private ProjectTreeVersionDO buildingVersion(Long rootId, long treeVersion, String changeBatchId) {
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setRootProjectId(rootId);
        version.setTreeVersion(treeVersion);
        version.setStatus("BUILDING");
        version.setChangeBatchId(changeBatchId);
        version.setNodeCount(0);
        version.setPathCount(0);
        version.setVersion(0);
        return version;
    }

    private String failureReason(RuntimeException failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    public record ProjectionResult(Long rootProjectId, long treeVersion, int nodeCount, int pathCount) {
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }

    public record MoveProjectSubtreeResult(Long projectId, Long parentId, String changeBatchId,
                                           Long treeVersion, List<AffectedRootVersion> affectedRoots,
                                           boolean replayed) {
    }

    public record AffectedRootVersion(Long rootProjectId, Long treeVersion) {
    }
}
