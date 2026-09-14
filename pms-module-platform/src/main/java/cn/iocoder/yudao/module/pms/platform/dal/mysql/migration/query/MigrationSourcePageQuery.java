package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;
import java.util.List;
public record MigrationSourcePageQuery(Long tenantId, Long batchId, List<Long> sourceRecordIds) {}
