package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

public record DeliveryEvidenceRevisionAdvance(Long tenantId, Long evidenceId,
                                              Integer expectedRevision, Integer expectedVersion,
                                              Long actorUserId) {
}
