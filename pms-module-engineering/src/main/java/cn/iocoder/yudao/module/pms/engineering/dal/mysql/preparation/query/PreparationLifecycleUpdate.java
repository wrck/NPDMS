package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

import java.time.LocalDateTime;

public record PreparationLifecycleUpdate(Long tenantId, Long preparationId, Integer expectedVersion,
                                         String expectedStatusCode, String statusCode,
                                         LocalDateTime submittedAt, LocalDateTime confirmedAt,
                                         LocalDateTime returnedAt, String returnReason, String updater) {}
