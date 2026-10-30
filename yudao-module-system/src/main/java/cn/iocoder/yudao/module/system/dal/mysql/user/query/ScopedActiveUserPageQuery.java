package cn.iocoder.yudao.module.system.dal.mysql.user.query;

import java.util.Set;

/** SYSTEM-only identity lookup, after its trusted owner provider resolves current eligibility. */
public record ScopedActiveUserPageQuery(Long tenantId, Set<Long> userIds, String keyword, int pageNo, int pageSize, Set<Long> matchedDepartmentIds) {
    public long offset() { return (pageNo - 1L) * pageSize; }
}
