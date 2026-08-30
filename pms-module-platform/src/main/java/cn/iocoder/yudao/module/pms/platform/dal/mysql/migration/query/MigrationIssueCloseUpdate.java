package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

import java.time.LocalDateTime;

public record MigrationIssueCloseUpdate(Long tenantId, Long issueId, int expectedVersion,
                                        Long resolverUserId, String ruleVersion,
                                        String targetResult, LocalDateTime resolvedAt) {
}
