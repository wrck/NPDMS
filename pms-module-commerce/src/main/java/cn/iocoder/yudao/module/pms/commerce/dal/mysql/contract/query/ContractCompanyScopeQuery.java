package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

import java.util.List;

public record ContractCompanyScopeQuery(
        Long tenantId, List<String> companyCodes, List<Long> projectIds, String companyCode, String contractNo,
        String contractType, String customerKeyword, String sourceSystem, String status,
        int offset, int limit) {
}
