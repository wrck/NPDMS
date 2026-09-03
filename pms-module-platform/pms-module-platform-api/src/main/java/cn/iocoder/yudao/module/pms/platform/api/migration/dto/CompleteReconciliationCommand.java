package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record CompleteReconciliationCommand(
        Long tenantId,
        Long batchId,
        int expectedBatchVersion,
        long expectedSourceCount,
        long expectedMappedCount,
        long expectedIssueCount,
        long expectedRetainedCount,
        String ruleVersion,
        String idempotencyKey,
        String correlationId) {

    public CompleteReconciliationCommand {
        tenantId = positive(tenantId, "tenantId");
        batchId = positive(batchId, "batchId");
        expectedBatchVersion = nonNegative(expectedBatchVersion, "expectedBatchVersion");
        expectedSourceCount = nonNegative(expectedSourceCount, "expectedSourceCount");
        expectedMappedCount = nonNegative(expectedMappedCount, "expectedMappedCount");
        expectedIssueCount = nonNegative(expectedIssueCount, "expectedIssueCount");
        expectedRetainedCount = nonNegative(expectedRetainedCount, "expectedRetainedCount");
        if (expectedSourceCount != expectedMappedCount + expectedIssueCount + expectedRetainedCount) {
            throw invalid("expected counts must reconcile");
        }
        ruleVersion = text(ruleVersion, 64, "ruleVersion");
        idempotencyKey = text(idempotencyKey, 128, "idempotencyKey");
        correlationId = text(correlationId, 128, "correlationId");
    }
}
