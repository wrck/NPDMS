package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query;

import java.util.Set;

public record ProjectTreeParentsQuery(Long tenantId, Long rootProjectId, Long treeVersion, Set<Long> projectIds) { }
