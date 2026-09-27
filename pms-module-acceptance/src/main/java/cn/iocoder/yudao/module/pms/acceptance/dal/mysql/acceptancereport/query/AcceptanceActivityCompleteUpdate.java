package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query;

public record AcceptanceActivityCompleteUpdate(Long tenantId, Long acceptanceId,
                                               Long expectedVersion, String updater) {
}
