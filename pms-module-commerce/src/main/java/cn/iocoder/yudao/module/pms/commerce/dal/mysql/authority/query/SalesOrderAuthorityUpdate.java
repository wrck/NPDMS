package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;

public record SalesOrderAuthorityUpdate(Long tenantId, SalesOrderDO row, Integer expectedVersion) {
}
