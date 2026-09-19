package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query;

import java.util.List;

/** Explicit native inventory page; null objectIds means project scope, an empty list means no objects. */
public record ReportResultInventoryQuery(Long tenantId, Long projectId, List<Long> objectIds,
                                   Long afterId, int limit, boolean historical) {
    public ReportResultInventoryQuery { objectIds = objectIds == null ? null : List.copyOf(objectIds); }
}
