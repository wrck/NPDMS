package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query;

public record InspectionRulePublicationLockQuery(
        Long tenantId,
        Long ruleId,
        Long targetRevisionId) {
}
