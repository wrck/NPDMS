package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

public record CutoverTaskSourceInvalidationUpdate(Long tenantId, Long taskId, Long expectedVersion) {
}
