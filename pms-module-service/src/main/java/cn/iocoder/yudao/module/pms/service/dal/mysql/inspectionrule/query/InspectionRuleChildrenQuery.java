package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query;

import java.util.Set;

public record InspectionRuleChildrenQuery(
        Long tenantId,
        Set<Long> revisionIds,
        String contentDigest) {

    public InspectionRuleChildrenQuery {
        revisionIds = revisionIds == null ? null : Set.copyOf(revisionIds);
    }
}
