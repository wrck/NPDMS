package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query;

import java.util.List;
public record ProjectScopeRootPathsQuery(Long tenantId, List<TreeVersion> trees) {
    public record TreeVersion(Long rootProjectId, Long treeVersion) {}
}
