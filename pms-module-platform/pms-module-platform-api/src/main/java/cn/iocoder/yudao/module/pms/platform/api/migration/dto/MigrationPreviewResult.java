package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;

import java.util.List;

/** 迁移预演结果：逐条分类与汇总计数，全过程只读。 */
public record MigrationPreviewResult(
        String sourceSystem,
        String sourceTable,
        List<MigrationPreviewItem> items,
        long noExistingSourceCount,
        long checksumConflictCount,
        long unmappedCount,
        long retainedCount,
        long mappedCount,
        long ambiguousCount) {

    public MigrationPreviewResult {
        try {
            sourceSystem = MigrationEvidenceContractRules.text(sourceSystem, 32, "sourceSystem");
            sourceTable = MigrationEvidenceContractRules.text(sourceTable, 64, "sourceTable");
            items = List.copyOf(MigrationEvidenceContractRules.completeList(items, "items"));
            noExistingSourceCount = MigrationEvidenceContractRules.nonNegative(noExistingSourceCount, "noExistingSourceCount");
            checksumConflictCount = MigrationEvidenceContractRules.nonNegative(checksumConflictCount, "checksumConflictCount");
            unmappedCount = MigrationEvidenceContractRules.nonNegative(unmappedCount, "unmappedCount");
            retainedCount = MigrationEvidenceContractRules.nonNegative(retainedCount, "retainedCount");
            mappedCount = MigrationEvidenceContractRules.nonNegative(mappedCount, "mappedCount");
            ambiguousCount = MigrationEvidenceContractRules.nonNegative(ambiguousCount, "ambiguousCount");
        } catch (PlatformMigrationEvidenceException ex) {
            throw ex;
        }
    }
}
