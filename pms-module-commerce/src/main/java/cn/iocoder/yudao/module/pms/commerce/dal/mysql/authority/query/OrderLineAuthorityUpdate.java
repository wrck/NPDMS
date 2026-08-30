package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.OrderLineDO;

public record OrderLineAuthorityUpdate(Long tenantId, OrderLineDO row, Integer expectedVersion) {
}
