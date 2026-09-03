package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

public record ArrivalDraftMutation(Long tenantId, Long arrivalAcceptanceId, Integer expectedVersion,
                                   String logisticsNo, LocalDateTime arrivedAt, String signerSnapshot,
                                   Long actorUserId) {
}
