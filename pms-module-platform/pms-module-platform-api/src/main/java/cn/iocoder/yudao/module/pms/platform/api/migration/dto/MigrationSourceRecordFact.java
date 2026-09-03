package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record MigrationSourceRecordFact(
        Long sourceRecordId,
        Long tenantId,
        Long batchId,
        String sourceSystem,
        String sourceTable,
        String sourcePk,
        String sourceBusinessKey,
        String sourcePayloadJson,
        String sourceChecksum,
        LocalDateTime extractedAt,
        String resultType) {

    public MigrationSourceRecordFact {
        try {
            sourceRecordId = positive(sourceRecordId, "sourceRecordId");
            tenantId = positive(tenantId, "tenantId");
            batchId = positive(batchId, "batchId");
            sourceSystem = text(sourceSystem, 32, "sourceSystem");
            sourceTable = text(sourceTable, 64, "sourceTable");
            sourcePk = text(sourcePk, 128, "sourcePk");
            sourceBusinessKey = optionalText(sourceBusinessKey, 512, "sourceBusinessKey");
            sourcePayloadJson = json(sourcePayloadJson, "sourcePayloadJson");
            sourceChecksum = sha256(sourceChecksum, "sourceChecksum");
            extractedAt = time(extractedAt, "extractedAt");
            resultType = optionalText(resultType, 16, "resultType");
        } catch (cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException ex) {
            throw corrupted(ex.getMessage());
        }
        if (resultType != null && !resultType.matches("MAPPED|ISSUE|RETAINED")) {
            throw corrupted("unknown resultType");
        }
    }
}
