package cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query;

import java.util.Set;

public record ProjectClosureGuardListQuery(Long tenantId, Set<Long> projectIds) {
}
