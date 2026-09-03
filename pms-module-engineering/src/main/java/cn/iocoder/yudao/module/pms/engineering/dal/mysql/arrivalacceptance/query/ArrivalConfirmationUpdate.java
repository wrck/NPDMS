package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

public record ArrivalConfirmationUpdate(
        Long tenantId,
        Long arrivalAcceptanceId,
        Integer expectedVersion,
        Long projectFactVersion,
        Long confirmedBy,
        LocalDateTime confirmedAt) {
}
