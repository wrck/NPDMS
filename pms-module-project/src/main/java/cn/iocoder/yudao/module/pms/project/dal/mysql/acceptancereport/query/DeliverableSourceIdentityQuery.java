package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query;

public record DeliverableSourceIdentityQuery(Long tenantId, Long deliverableId,
                                             Long sourceObjectId, Integer sourceVersion) {
}
