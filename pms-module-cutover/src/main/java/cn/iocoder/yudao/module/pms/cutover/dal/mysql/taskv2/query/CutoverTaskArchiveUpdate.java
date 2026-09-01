package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

public record CutoverTaskArchiveUpdate(Long tenantId, Long taskId, Integer expectedVersion) {
}
