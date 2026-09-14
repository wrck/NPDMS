package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;
import java.util.List;
public record MigrationSourceKeysQuery(Long tenantId, Long batchId, List<String> sourceKeys) {}
