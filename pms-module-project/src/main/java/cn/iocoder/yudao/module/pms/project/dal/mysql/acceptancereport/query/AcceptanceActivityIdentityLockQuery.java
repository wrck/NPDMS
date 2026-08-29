package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query;

public record AcceptanceActivityIdentityLockQuery(
        Long tenantId,
        Long projectId,
        String acceptanceType) {
}
