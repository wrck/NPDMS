package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record CreateImportBatchCommand(
        Long tenantId,
        String ownerContextCode,
        String purposeCode,
        String releaseId,
        String sourceSystem,
        String sourceTable,
        String manifestSchemaVersion,
        long expectedRowCount,
        String contentSha256,
        LocalDateTime exportedAt,
        Long previousBatchId,
        Long previousIssueId,
        String idempotencyKey,
        String correlationId) {

    public CreateImportBatchCommand {
        tenantId = positive(tenantId, "tenantId");
        ownerContextCode = text(ownerContextCode, 32, "ownerContextCode");
        purposeCode = text(purposeCode, 64, "purposeCode");
        releaseId = text(releaseId, 128, "releaseId");
        sourceSystem = text(sourceSystem, 32, "sourceSystem");
        sourceTable = text(sourceTable, 64, "sourceTable");
        manifestSchemaVersion = text(manifestSchemaVersion, 64, "manifestSchemaVersion");
        expectedRowCount = nonNegative(expectedRowCount, "expectedRowCount");
        contentSha256 = sha256(contentSha256, "contentSha256");
        exportedAt = time(exportedAt, "exportedAt");
        if (previousBatchId != null) {
            previousBatchId = positive(previousBatchId, "previousBatchId");
        }
        if (previousIssueId != null) {
            previousIssueId = positive(previousIssueId, "previousIssueId");
        }
        idempotencyKey = text(idempotencyKey, 128, "idempotencyKey");
        correlationId = text(correlationId, 128, "correlationId");
    }
}
