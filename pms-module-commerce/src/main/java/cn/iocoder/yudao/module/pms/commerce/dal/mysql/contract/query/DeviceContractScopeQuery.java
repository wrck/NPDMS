package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

import java.util.List;

public record DeviceContractScopeQuery(Long tenantId, List<String> companyCodes, List<Long> projectIds,
                                      java.util.Set<String> contractNumbers) {
    public DeviceContractScopeQuery(Long tenantId, List<String> companyCodes, List<Long> projectIds) {
        this(tenantId, companyCodes, projectIds, null);
    }
}
