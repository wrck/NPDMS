package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query;

import java.util.Set;

public record SatisfactionResultScopeQuery(Long tenantId, Set<Long> projectIds, Long resultId) {
}
