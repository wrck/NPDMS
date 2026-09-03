package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query;

public record InspectionRuleRevisionKeyQuery(
        Long tenantId,
        Long ruleId,
        Integer revisionNo) {
}
