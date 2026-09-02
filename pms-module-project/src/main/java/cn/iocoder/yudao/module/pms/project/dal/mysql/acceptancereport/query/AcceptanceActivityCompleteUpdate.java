package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query;

public record AcceptanceActivityCompleteUpdate(Long tenantId, Long acceptanceId,
                                               Integer expectedVersion, String updater) {
}
