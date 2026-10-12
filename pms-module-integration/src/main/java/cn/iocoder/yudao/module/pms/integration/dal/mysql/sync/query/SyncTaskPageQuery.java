package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.query;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;

public record SyncTaskPageQuery(Long tenantId, long offset, int limit) {
    public SyncTaskPageQuery {
        if (tenantId == null || offset < 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Task paging requires a tenant and a bounded page");
        }
    }

    public static SyncTaskPageQuery from(SyncQueries.Page page) {
        if (page.getPageNo() == null || page.getPageNo() < 1 || page.getPageSize() == null) {
            throw new IllegalArgumentException("Invalid task page");
        }
        return new SyncTaskPageQuery(page.getTenantId(),
                ((long) page.getPageNo() - 1) * page.getPageSize(), page.getPageSize());
    }
}
