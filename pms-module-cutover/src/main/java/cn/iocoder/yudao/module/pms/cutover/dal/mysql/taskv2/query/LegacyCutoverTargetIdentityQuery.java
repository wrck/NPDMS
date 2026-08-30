package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

public record LegacyCutoverTargetIdentityQuery(Long tenantId, Long projectId, String taskNo,
                                                Long legacyTaskId) {
}
