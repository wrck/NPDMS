package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection;

public record SelectableInspectionRuleProjection(
        Long ruleId,
        String detectionId,
        Long revisionId,
        Integer revisionNo,
        String categoryCode,
        String categoryName,
        String inspectionItem,
        String severityCode,
        String severityName,
        Integer sortOrder,
        String productTypeCode,
        String productTypeName) {
}
