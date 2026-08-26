package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

import java.time.LocalDateTime;

public record PreparationItemReviewUpdate(Long tenantId, Long preparationId, Long itemId,
        Integer expectedVersion, String expectedConfirmationStatus, String applicabilityCode,
        String confirmationStatusCode, String notApplicableReason, Long actorUserId,
        LocalDateTime decidedAt, String returnReason, String updater) {}
