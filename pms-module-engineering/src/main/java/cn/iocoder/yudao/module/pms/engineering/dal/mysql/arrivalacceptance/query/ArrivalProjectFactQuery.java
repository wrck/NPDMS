package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

public record ArrivalProjectFactQuery(Long tenantId, Long projectId, LocalDateTime checkedAt) {

    public ArrivalProjectFactQuery {
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || checkedAt == null) {
            throw new IllegalArgumentException("invalid arrival project fact query");
        }
    }
}
