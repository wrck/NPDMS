package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query;

public record AcceptanceActivityIdentityLockQuery(
        Long tenantId,
        Long projectId,
        String acceptanceType) {
}
