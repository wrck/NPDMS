package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

import java.util.List;

/**
 * 跨批次的来源全局身份查询：同租户 + 来源系统 + 来源表下按 sourceRecordKey 检索，
 * 供迁移预演在不绑定 batchId 的前提下识别既有来源记录。
 */
public record MigrationSourceGlobalIdentityQuery(Long tenantId, String sourceSystem, String sourceTable,
                                                 List<String> sourceKeys) {
}
