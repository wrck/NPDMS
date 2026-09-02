package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

import java.util.List;

public record MigrationBatchClaimQuery(Long tenantId, String ownerContextCode, String purposeCode,
                                       List<String> sourceSystems, List<String> sourceTables) {
}
