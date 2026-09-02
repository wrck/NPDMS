package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command;

public record InspectionRuleProductTypeNameUpdate(
        Long tenantId,
        Long revisionId,
        String productTypeCode,
        String productTypeNameSnapshot) {
}
