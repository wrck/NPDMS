package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;

import java.util.List;

/** 迁移预演单条结论：分类 + 命中的既有来源记录与映射目标（只读）。 */
public record MigrationPreviewItem(
        String sourcePk,
        MigrationPreviewClassification classification,
        Long latestSourceRecordId,
        Long latestBatchId,
        String latestSourceChecksum,
        List<MigrationPreviewTarget> targets) {

    public MigrationPreviewItem {
        try {
            sourcePk = MigrationEvidenceContractRules.text(sourcePk, 128, "sourcePk");
            if (classification == null) {
                throw MigrationEvidenceContractRules.corrupted("classification must not be null");
            }
            if (latestSourceRecordId != null) {
                latestSourceRecordId = MigrationEvidenceContractRules.positive(latestSourceRecordId, "latestSourceRecordId");
            }
            if (latestBatchId != null) {
                latestBatchId = MigrationEvidenceContractRules.positive(latestBatchId, "latestBatchId");
            }
            targets = List.copyOf(MigrationEvidenceContractRules.completeList(targets, "targets"));
        } catch (PlatformMigrationEvidenceException ex) {
            throw ex;
        }
    }
}
