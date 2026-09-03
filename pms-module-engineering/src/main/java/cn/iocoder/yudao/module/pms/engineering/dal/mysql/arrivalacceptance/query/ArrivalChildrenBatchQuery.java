package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.util.Set;

public record ArrivalChildrenBatchQuery(Long tenantId, Set<Long> arrivalAcceptanceIds) {
    public ArrivalChildrenBatchQuery {
        if (tenantId == null || tenantId < 0 || arrivalAcceptanceIds == null
                || arrivalAcceptanceIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("invalid arrival children batch query");
        }
        arrivalAcceptanceIds = Set.copyOf(arrivalAcceptanceIds);
    }
}
