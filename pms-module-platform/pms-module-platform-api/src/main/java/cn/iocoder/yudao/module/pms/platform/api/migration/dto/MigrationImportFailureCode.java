package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

public enum MigrationImportFailureCode {
    MANIFEST_STRUCTURE_INVALID,
    MANIFEST_ROW_COUNT_MISMATCH,
    MANIFEST_SCHEMA_VERSION_MISMATCH,
    MANIFEST_CONTENT_SHA256_MISMATCH,
    SOURCE_PAYLOAD_INVALID,
    SOURCE_RECORD_CONFLICT
}
