package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query;

import java.util.List;

/** Explicit native inventory page; null objectIds means project scope, an empty list means no objects. */
public record SurveyResultInventoryQuery(Long tenantId, Long projectId, List<Long> objectIds,
                                   Long afterId, int limit, boolean historical) {
    public SurveyResultInventoryQuery { objectIds = objectIds == null ? null : List.copyOf(objectIds); }
}
