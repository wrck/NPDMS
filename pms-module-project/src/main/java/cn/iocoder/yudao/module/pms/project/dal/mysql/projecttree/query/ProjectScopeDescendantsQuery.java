package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
public record ProjectScopeDescendantsQuery(Long tenantId, List<Subtree> subtrees) {
    public record Subtree(Long rootProjectId, Long treeVersion, Set<Long> ancestorProjectIds) {}

    /** Keep each ancestor tied to its exact root/version without a deeply nested OR expression. */
    public List<AncestorPath> getPaths() {
        if (subtrees == null || subtrees.isEmpty()) return List.of();
        List<AncestorPath> paths = new ArrayList<>();
        for (Subtree subtree : subtrees) {
            if (subtree.ancestorProjectIds() == null) continue;
            for (Long ancestorId : subtree.ancestorProjectIds()) {
                paths.add(new AncestorPath(subtree.rootProjectId(), subtree.treeVersion(), ancestorId));
            }
        }
        return paths;
    }

    public record AncestorPath(Long rootProjectId, Long treeVersion, Long ancestorProjectId) {}
}
