package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

import java.util.List;

/** Tenant-scoped lookup of the incoming migration page and its parent orders. */
public record ErpOrderSyncQuery(Long tenantId, List<String> orderNumbers) {}
