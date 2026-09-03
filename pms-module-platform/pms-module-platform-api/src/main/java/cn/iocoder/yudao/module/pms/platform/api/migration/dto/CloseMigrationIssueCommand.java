package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record CloseMigrationIssueCommand(
        Long tenantId,
        Long issueId,
        Long resolverUserId,
        String ruleVersion,
        String targetResultJson,
        String idempotencyKey,
        String correlationId) {

    public CloseMigrationIssueCommand {
        tenantId = positive(tenantId, "tenantId");
        issueId = positive(issueId, "issueId");
        resolverUserId = positive(resolverUserId, "resolverUserId");
        ruleVersion = text(ruleVersion, 64, "ruleVersion");
        targetResultJson = json(targetResultJson, "targetResultJson");
        idempotencyKey = text(idempotencyKey, 128, "idempotencyKey");
        correlationId = text(correlationId, 128, "correlationId");
    }
}
