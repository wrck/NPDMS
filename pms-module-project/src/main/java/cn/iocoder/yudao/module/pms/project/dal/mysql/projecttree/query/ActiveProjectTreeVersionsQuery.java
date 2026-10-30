package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query;

import java.util.Set;
public record ActiveProjectTreeVersionsQuery(Long tenantId, Set<Long> rootProjectIds) {}
