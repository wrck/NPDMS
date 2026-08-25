package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

public record TaskVersionUpdate(Long tenantId, Long taskId, Integer expectedVersion, String updater) {
}
