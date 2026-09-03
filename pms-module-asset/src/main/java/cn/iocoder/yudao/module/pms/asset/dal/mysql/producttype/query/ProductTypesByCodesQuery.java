package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query;

import java.util.Set;

public record ProductTypesByCodesQuery(
        Long tenantId,
        Set<String> productTypeCodes) {

    public ProductTypesByCodesQuery {
        productTypeCodes = productTypeCodes == null ? null : Set.copyOf(productTypeCodes);
    }
}
