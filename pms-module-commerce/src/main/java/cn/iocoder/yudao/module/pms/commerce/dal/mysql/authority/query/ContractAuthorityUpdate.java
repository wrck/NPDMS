package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.ContractDO;

public record ContractAuthorityUpdate(Long tenantId, ContractDO row, Integer expectedVersion) {
}
