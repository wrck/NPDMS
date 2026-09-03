package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query;

public record DeliverableSourceObjectIdentityQuery(Long tenantId, Long deliverableId,
        String sourceObjectType, Long sourceObjectId, Integer sourceVersion) {
}
