package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query;

public record SatisfactionTaskAssignmentUpdate(Long tenantId, Long taskId, Integer expectedVersion,
                                                Long assignedToUserId, Long assignedByUserId, String updater) {
}
