package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query;

import java.util.Set;

public record PendingArchiveSourceTypeQuery(Long tenantId, String sourceObjectType,
                                            Set<String> relationStatuses, int limit) {
}
