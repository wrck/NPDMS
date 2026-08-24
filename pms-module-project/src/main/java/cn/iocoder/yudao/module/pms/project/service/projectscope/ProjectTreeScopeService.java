package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class ProjectTreeScopeService {
    private static final Set<String> TREE_MANAGER_ROLES = Set.of(
            "PROJECT_MANAGER", "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");

    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTreePathMapper pathMapper;

    public ProjectTreeScope resolve(Long actorId, Long anchorProjectId, long treeVersion) {
        if (actorId == null || anchorProjectId == null || treeVersion <= 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        ProjectMasterDO anchor = projectMapper.selectById(anchorProjectId);
        if (anchor == null) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        long rootId = anchor.getRootId() == null ? anchor.getId() : anchor.getRootId();
        Set<Long> rootNodes = pathMapper.selectByAncestor(rootId, treeVersion, rootId, null).stream()
                .map(ProjectTreePathDO::getDescendantProjectId).collect(Collectors.toSet());
        List<ProjectMemberAssignmentDO> assignments = memberMapper.selectActiveByUser(actorId, LocalDateTime.now())
                .stream().filter(item -> rootNodes.contains(item.getProjectId())).toList();
        Set<Long> direct = assignments.stream().map(ProjectMemberAssignmentDO::getProjectId)
                .collect(Collectors.toSet());
        Set<Long> managerNodes = assignments.stream()
                .filter(item -> TREE_MANAGER_ROLES.contains(item.getMemberRole()))
                .map(ProjectMemberAssignmentDO::getProjectId).collect(Collectors.toSet());
        Set<Long> full = new HashSet<>(direct);
        pathMapper.selectByAncestors(rootId, treeVersion, managerNodes).stream()
                .map(ProjectTreePathDO::getDescendantProjectId).forEach(full::add);
        Set<Long> placeholders = new HashSet<>();
        pathMapper.selectByDescendants(rootId, treeVersion, full).stream()
                .map(ProjectTreePathDO::getAncestorProjectId).forEach(placeholders::add);
        placeholders.removeAll(full);
        return new ProjectTreeScope(rootId, treeVersion, Set.copyOf(full), Set.copyOf(placeholders),
                managerNodes.isEmpty() ? Set.of() : Set.copyOf(rootNodes));
    }

    public void assertFullAccess(Long actorId, Long projectId, long treeVersion) {
        if (resolve(actorId, projectId, treeVersion).visibility(projectId) != Visibility.FULL) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    public enum Visibility { FULL, ROOT_SUMMARY, PATH_PLACEHOLDER, NONE }

    public record ProjectTreeScope(long rootProjectId, long treeVersion, Set<Long> fullProjectIds,
                                   Set<Long> placeholderProjectIds, Set<Long> summaryProjectIds) {
        public Visibility visibility(Long projectId) {
            if (fullProjectIds.contains(projectId)) return Visibility.FULL;
            if (summaryProjectIds.contains(projectId)) return Visibility.ROOT_SUMMARY;
            if (placeholderProjectIds.contains(projectId)) return Visibility.PATH_PLACEHOLDER;
            return Visibility.NONE;
        }

        public Set<Long> visibleProjectIds() {
            Set<Long> visible = new HashSet<>(fullProjectIds);
            visible.addAll(placeholderProjectIds);
            visible.addAll(summaryProjectIds);
            return Set.copyOf(visible);
        }
    }
}
