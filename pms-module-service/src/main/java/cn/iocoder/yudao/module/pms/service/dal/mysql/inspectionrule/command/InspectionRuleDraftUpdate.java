package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command;

import java.math.BigDecimal;

public record InspectionRuleDraftUpdate(
        Long tenantId,
        Long revisionId,
        Integer expectedVersion,
        String inspectionItem,
        String description,
        String categoryCode,
        String categoryNameSnapshot,
        String severityCode,
        String severityNameSnapshot,
        Integer sortOrder,
        String expectedResultRegex,
        String thresholdDataType,
        String thresholdOperator,
        BigDecimal thresholdValue,
        String thresholdUnit) {
}
