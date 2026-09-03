package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

public record CutoverTaskChecklistSubmitUpdate(Long tenantId, Long taskId, Integer expectedVersion) {
}
