package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query;

import java.util.List;

/** Explicit native inventory page; null objectIds means project scope, an empty list means no objects. */
public record RequirementResultInventoryQuery(Long tenantId, Long projectId, List<Long> objectIds,
                                   Long afterId, int limit, boolean historical) {
    public RequirementResultInventoryQuery { objectIds = objectIds == null ? null : List.copyOf(objectIds); }
}
