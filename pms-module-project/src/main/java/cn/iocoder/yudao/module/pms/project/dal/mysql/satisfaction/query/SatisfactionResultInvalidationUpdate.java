package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query;

import java.time.LocalDateTime;

public record SatisfactionResultInvalidationUpdate(
        Long tenantId, Long resultId, Integer expectedFactVersion, String reasonCode, String reasonSummary,
        Long actorUserId, LocalDateTime invalidatedAt, String updater) {
}
