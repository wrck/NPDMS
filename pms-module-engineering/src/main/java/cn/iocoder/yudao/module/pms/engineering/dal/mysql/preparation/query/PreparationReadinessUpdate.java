package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record PreparationReadinessUpdate(Long tenantId, Long preparationId,
                                         Integer expectedVersion, Integer expectedInputVersion,
                                         Integer expectedReadinessVersion, String readinessStatusCode,
                                         Long latestReadinessSnapshotId, Boolean snapshotCurrent,
                                         String updater) {}
