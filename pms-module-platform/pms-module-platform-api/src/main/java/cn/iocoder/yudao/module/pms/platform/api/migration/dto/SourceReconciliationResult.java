package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record SourceReconciliationResult(
        Long sourceRecordId,
        SourceReconciliationType resultType,
        List<Long> mappingIds) {

    public SourceReconciliationResult {
        try {
            sourceRecordId = positive(sourceRecordId, "sourceRecordId");
            if (resultType == null) {
                throw corrupted("resultType must not be null");
            }
            List<Long> normalizedMappingIds = completeList(mappingIds, "mappingIds").stream()
                    .map(value -> positive(value, "mappingId"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
            if (normalizedMappingIds.stream().distinct().count() != normalizedMappingIds.size()) {
                throw corrupted("mappingIds must be unique");
            }
            mappingIds = normalizedMappingIds;
        } catch (cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException ex) {
            throw corrupted(ex.getMessage());
        }
        if (resultType == SourceReconciliationType.MAPPED && mappingIds.isEmpty()) {
            throw corrupted("MAPPED requires mappingIds");
        }
        if (resultType == SourceReconciliationType.RETAINED && !mappingIds.isEmpty()) {
            throw corrupted("RETAINED forbids mappingIds");
        }
    }
}
