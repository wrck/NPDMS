package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

import java.time.LocalDateTime;

public record PreparationSourceSyncUpdate(Long tenantId, Long preparationId, Long itemId,
                                          Long sourceReferenceId, Integer expectedVersion,
                                          String syncStatusCode, String normalizedResultCode,
                                          String sourceFactVersion, String sourceWatermark,
                                          String lastSuccessResultCode, String lastSuccessFactVersion,
                                          String lastSuccessWatermark, LocalDateTime lastSuccessAt,
                                          LocalDateTime lastSyncedAt, String lastSyncErrorCode,
                                          String updater) {}
