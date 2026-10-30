package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

import java.util.List;

/**
 * 按来源记录ID集合检索外部键映射：不限定 batchId（同一来源身份可能跨批次出现），
 * 供迁移预演汇总既有映射结果。
 */
public record MigrationMappingSourceQuery(Long tenantId, List<Long> sourceRecordIds) {
}
