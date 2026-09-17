package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query;

public record DeliverableSourceIdentityQuery(Long tenantId, Long deliverableId,
                                             Long sourceObjectId, Integer sourceVersion) {
}
