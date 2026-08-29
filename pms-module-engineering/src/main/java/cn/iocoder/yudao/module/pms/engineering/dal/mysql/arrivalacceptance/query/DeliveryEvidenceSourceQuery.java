package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

public record DeliveryEvidenceSourceQuery(
        Long tenantId,
        String sourceRequirement,
        String sourceObjectType,
        Long sourceObjectId) {
}
