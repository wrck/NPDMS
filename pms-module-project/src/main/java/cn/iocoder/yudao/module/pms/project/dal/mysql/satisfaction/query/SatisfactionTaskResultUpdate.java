package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query;

public record SatisfactionTaskResultUpdate(Long tenantId, Long taskId, Integer expectedVersion,
                                           Long resultId, String targetStatus, String updater) {
}
