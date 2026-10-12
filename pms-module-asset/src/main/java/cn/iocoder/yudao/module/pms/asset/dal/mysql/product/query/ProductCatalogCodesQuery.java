package cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query;

import java.util.Set;

/** Directory labels for the products referenced by one authorized shipment page. */
public record ProductCatalogCodesQuery(Long tenantId, Set<String> productCodes) {}
