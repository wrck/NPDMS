package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.query;

/** 当前任务的来源身份映射分页，范围由同步服务解析。 */
public record SyncBindingPageQuery(Long tenantId, Long taskId, long offset, int limit) {}
