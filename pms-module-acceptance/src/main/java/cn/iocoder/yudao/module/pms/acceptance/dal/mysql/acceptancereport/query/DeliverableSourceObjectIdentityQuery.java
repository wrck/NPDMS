package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query;

public record DeliverableSourceObjectIdentityQuery(Long tenantId, Long deliverableId,
        String sourceObjectType, Long sourceObjectId, Integer sourceVersion) {
}
