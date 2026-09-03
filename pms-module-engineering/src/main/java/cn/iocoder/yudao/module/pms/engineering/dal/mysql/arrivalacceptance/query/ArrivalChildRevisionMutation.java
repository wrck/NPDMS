package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

public record ArrivalChildRevisionMutation(Long tenantId, Long arrivalAcceptanceId, Long childId,
                                           Integer expectedVersion, Long actorUserId) {
}
