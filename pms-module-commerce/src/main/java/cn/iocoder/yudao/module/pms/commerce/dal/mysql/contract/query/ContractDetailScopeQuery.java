package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

import java.util.List;

public record ContractDetailScopeQuery(
        Long tenantId, Long contractId, List<String> companyCodes, List<Long> projectIds) {
}
