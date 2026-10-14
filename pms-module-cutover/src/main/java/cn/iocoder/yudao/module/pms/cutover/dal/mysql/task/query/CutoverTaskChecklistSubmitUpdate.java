package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

public record CutoverTaskChecklistSubmitUpdate(Long tenantId, Long taskId, Long expectedVersion) {
}
