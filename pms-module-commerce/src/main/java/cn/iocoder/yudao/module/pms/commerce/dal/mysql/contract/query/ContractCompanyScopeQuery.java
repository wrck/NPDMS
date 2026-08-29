package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

import java.util.List;

public record ContractCompanyScopeQuery(
        Long tenantId, List<String> companyCodes, String contractNo, String status, int offset, int limit) {
}
