package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

public enum MigrationBatchStatus {
    IMPORTING,
    STAGED_READY,
    RECONCILING,
    COMPLETED,
    FAILED
}
