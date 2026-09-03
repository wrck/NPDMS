package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.util.Set;

public record ArrivalPageQuery(
        Long tenantId,
        Set<Long> visibleProjectIds,
        Long projectId,
        String batchCode,
        String status,
        int offset,
        int limit) {

    public ArrivalPageQuery {
        if (tenantId == null || tenantId < 0 || visibleProjectIds == null
                || projectId != null && projectId <= 0
                || batchCode != null && batchCode.isBlank()
                || offset < 0 || limit <= 0 || limit > 200) {
            throw new IllegalArgumentException("invalid arrival acceptance page query");
        }
        visibleProjectIds = Set.copyOf(visibleProjectIds);
    }

    public ArrivalPageQuery(Long tenantId, Set<Long> visibleProjectIds, String status,
                            int offset, int limit) {
        this(tenantId, visibleProjectIds, null, null, status, offset, limit);
    }
}
