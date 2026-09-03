package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record AppendMigrationIssueCommand(
        Long tenantId,
        Long batchId,
        Long sourceRecordId,
        String issueKey,
        String issueType,
        String rawBusinessKey,
        List<Long> candidateTargetIds,
        String rawPayloadJson,
        String idempotencyKey,
        String correlationId) {

    public AppendMigrationIssueCommand {
        tenantId = positive(tenantId, "tenantId");
        batchId = positive(batchId, "batchId");
        sourceRecordId = positive(sourceRecordId, "sourceRecordId");
        issueKey = text(issueKey, 128, "issueKey");
        issueType = text(issueType, 64, "issueType");
        rawBusinessKey = optionalText(rawBusinessKey, 512, "rawBusinessKey");
        List<Long> normalizedTargetIds = completeList(candidateTargetIds, "candidateTargetIds").stream()
                .map(value -> positive(value, "candidateTargetId"))
                .sorted(Comparator.naturalOrder())
                .toList();
        if (normalizedTargetIds.stream().distinct().count() != normalizedTargetIds.size()) {
            throw invalid("candidateTargetIds must be unique");
        }
        candidateTargetIds = normalizedTargetIds;
        rawPayloadJson = optionalJson(rawPayloadJson, "rawPayloadJson");
        idempotencyKey = text(idempotencyKey, 128, "idempotencyKey");
        correlationId = text(correlationId, 128, "correlationId");
    }
}
