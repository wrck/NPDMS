package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query;

public record SatisfactionTaskResultUpdate(Long tenantId, Long taskId, Integer expectedVersion,
                                           Long resultId, String targetStatus, String updater) {
}
