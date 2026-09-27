package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

public record ArrivalResolutionMutation(Long tenantId, Long arrivalAcceptanceId,
                                        Long expectedVersion, String expectedStatus,
                                        String newStatus, Long evidenceId,
                                        Integer evidenceRevision, Long actorUserId) {
}
