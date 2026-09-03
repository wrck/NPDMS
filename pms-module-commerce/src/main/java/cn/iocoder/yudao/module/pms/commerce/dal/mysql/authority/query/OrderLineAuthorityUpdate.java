package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;

public record OrderLineAuthorityUpdate(Long tenantId, SalesOrderLineDO row, Integer expectedVersion) {
}
