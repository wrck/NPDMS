package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

import java.time.LocalDateTime;

public record RequirementAnalysisCompleteUpdate(
        Long tenantId, Long preparationId, Integer expectedVersion,
        Integer expectedContentVersion, Long completedBy, LocalDateTime completedAt, String updater) {
}
