package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record MarkStagedReadyCommand(
        Long tenantId,
        Long batchId,
        int expectedBatchVersion,
        ImportStagingDecision decision,
        Long manifestRowCount,
        String manifestSchemaVersion,
        String manifestContentSha256,
        MigrationImportFailureCode failureCode,
        String idempotencyKey,
        String correlationId) {

    public MarkStagedReadyCommand {
        tenantId = positive(tenantId, "tenantId");
        batchId = positive(batchId, "batchId");
        expectedBatchVersion = nonNegative(expectedBatchVersion, "expectedBatchVersion");
        if (decision == null) {
            throw invalid("decision must not be null");
        }
        if (decision == ImportStagingDecision.READY) {
            if (manifestRowCount == null) {
                throw invalid("manifestRowCount must not be null");
            }
            manifestRowCount = nonNegative(manifestRowCount, "manifestRowCount");
            manifestSchemaVersion = text(manifestSchemaVersion, 64, "manifestSchemaVersion");
            manifestContentSha256 = sha256(manifestContentSha256, "manifestContentSha256");
            if (failureCode != null) {
                throw invalid("READY forbids failureCode");
            }
        } else {
            if (manifestRowCount != null || manifestSchemaVersion != null || manifestContentSha256 != null) {
                throw invalid("FAIL_IMPORT forbids successful manifest facts");
            }
            if (failureCode == null) {
                throw invalid("FAIL_IMPORT requires failureCode");
            }
        }
        idempotencyKey = text(idempotencyKey, 128, "idempotencyKey");
        correlationId = text(correlationId, 128, "correlationId");
    }
}
