package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

public record ProjectContractIdentityLockQuery(
        Long tenantId, Long projectId, Long contractId, String relationRole) {
}
