package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

import java.util.Set;

public record ConstructionPlanRevisionListQuery(
        Long tenantId,
        Long planId,
        Set<Long> revisionIds
) {
}
