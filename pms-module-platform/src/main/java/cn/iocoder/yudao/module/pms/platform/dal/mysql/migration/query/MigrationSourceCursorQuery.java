package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

public record MigrationSourceCursorQuery(Long tenantId, Long batchId, Long afterSourceRecordId, int limit) {
}
