package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.corrupted;

public record MigrationBatchClaimResult(boolean claimed, MigrationBatchFact batch) {

    public MigrationBatchClaimResult {
        if (claimed != (batch != null)) {
            throw corrupted("claimed must match batch presence");
        }
        if (batch != null && batch.status() != MigrationBatchStatus.RECONCILING) {
            throw corrupted("claimed batch must be RECONCILING");
        }
    }

    public static MigrationBatchClaimResult empty() {
        return new MigrationBatchClaimResult(false, null);
    }
}
