package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;

public record ContractAuthorityUpdate(Long tenantId, ContractDO row, Integer expectedVersion) {
}
