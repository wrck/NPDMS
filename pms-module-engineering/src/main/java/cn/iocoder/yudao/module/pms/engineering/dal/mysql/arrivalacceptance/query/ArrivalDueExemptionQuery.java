package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

public record ArrivalDueExemptionQuery(LocalDateTime processingTime, Integer pageSize) {

    public ArrivalDueExemptionQuery {
        if (processingTime == null || pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new IllegalArgumentException("invalid due arrival exemption query");
        }
    }
}
