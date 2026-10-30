package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query;

public record SatisfactionTaskAssignmentUpdate(Long tenantId, Long taskId, Long expectedVersion,
                                                Long assignedToUserId, Long assignedByUserId, String updater) {
}
