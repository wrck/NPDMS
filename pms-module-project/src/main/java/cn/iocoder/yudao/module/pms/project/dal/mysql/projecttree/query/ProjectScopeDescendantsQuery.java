package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query;

import java.util.List;
import java.util.Set;
public record ProjectScopeDescendantsQuery(Long tenantId, List<Subtree> subtrees) {
    public record Subtree(Long rootProjectId, Long treeVersion, Set<Long> ancestorProjectIds) {}
}
