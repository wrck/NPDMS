package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.positive;

public record MigrationSourceRecordPageQuery(
        Long tenantId,
        Long batchId,
        Long afterSourceRecordId,
        int limit) {

    public MigrationSourceRecordPageQuery {
        tenantId = positive(tenantId, "tenantId");
        batchId = positive(batchId, "batchId");
        if (afterSourceRecordId != null) {
            afterSourceRecordId = positive(afterSourceRecordId, "afterSourceRecordId");
        }
        if (limit < 1 || limit > 500) {
            throw MigrationEvidenceContractRules.invalid("limit must be between 1 and 500");
        }
    }
}
