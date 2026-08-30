package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record MigrationBatchFact(
        Long batchId,
        Long tenantId,
        String ownerContextCode,
        String purposeCode,
        String releaseId,
        String sourceSystem,
        String sourceTable,
        MigrationBatchStatus status,
        long sourceCount,
        long mappedCount,
        long issueCount,
        long retainedCount,
        String failureCode,
        int version,
        LocalDateTime createTime) {

    public MigrationBatchFact {
        try {
            batchId = positive(batchId, "batchId");
            tenantId = positive(tenantId, "tenantId");
            ownerContextCode = text(ownerContextCode, 32, "ownerContextCode");
            purposeCode = text(purposeCode, 64, "purposeCode");
            releaseId = text(releaseId, 128, "releaseId");
            sourceSystem = text(sourceSystem, 32, "sourceSystem");
            sourceTable = text(sourceTable, 64, "sourceTable");
            if (status == null) {
                throw corrupted("status must not be null");
            }
            sourceCount = nonNegative(sourceCount, "sourceCount");
            mappedCount = nonNegative(mappedCount, "mappedCount");
            issueCount = nonNegative(issueCount, "issueCount");
            retainedCount = nonNegative(retainedCount, "retainedCount");
            failureCode = optionalText(failureCode, 64, "failureCode");
            version = nonNegative(version, "version");
            createTime = time(createTime, "createTime");
        } catch (cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException ex) {
            throw corrupted(ex.getMessage());
        }
        if (status == MigrationBatchStatus.FAILED && failureCode == null) {
            throw corrupted("FAILED requires failureCode");
        }
        if (status != MigrationBatchStatus.FAILED && failureCode != null) {
            throw corrupted("non-FAILED batch forbids failureCode");
        }
        if (status == MigrationBatchStatus.COMPLETED
                && sourceCount != mappedCount + issueCount + retainedCount) {
            throw corrupted("COMPLETED counts must reconcile");
        }
    }
}
