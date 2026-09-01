package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query;

import java.util.Set;

public record SelectableInspectionRuleQuery(
        Long tenantId,
        Set<String> productTypeCodes) {

    public SelectableInspectionRuleQuery {
        productTypeCodes = productTypeCodes == null ? null : Set.copyOf(productTypeCodes);
    }
}
