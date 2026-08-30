package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record MarkStagedReadyCommand(
        Long tenantId,
        Long batchId,
        int expectedBatchVersion,
        long manifestRowCount,
        String manifestSchemaVersion,
        String manifestContentSha256,
        String idempotencyKey,
        String correlationId) {

    public MarkStagedReadyCommand {
        tenantId = positive(tenantId, "tenantId");
        batchId = positive(batchId, "batchId");
        expectedBatchVersion = nonNegative(expectedBatchVersion, "expectedBatchVersion");
        manifestRowCount = nonNegative(manifestRowCount, "manifestRowCount");
        manifestSchemaVersion = text(manifestSchemaVersion, 64, "manifestSchemaVersion");
        manifestContentSha256 = sha256(manifestContentSha256, "manifestContentSha256");
        idempotencyKey = text(idempotencyKey, 128, "idempotencyKey");
        correlationId = text(correlationId, 128, "correlationId");
    }
}
