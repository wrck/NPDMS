package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record MigrationIssueFact(
        Long issueId,
        Long tenantId,
        Long batchId,
        Long sourceRecordId,
        String issueKey,
        String issueType,
        MigrationIssueStatus status,
        Long resolverUserId,
        String ruleVersion,
        String targetResultJson,
        LocalDateTime resolvedAt) {

    public MigrationIssueFact {
        try {
            issueId = positive(issueId, "issueId");
            tenantId = positive(tenantId, "tenantId");
            batchId = positive(batchId, "batchId");
            sourceRecordId = positive(sourceRecordId, "sourceRecordId");
            issueKey = text(issueKey, 128, "issueKey");
            issueType = text(issueType, 64, "issueType");
            if (status == null) {
                throw corrupted("status must not be null");
            }
            if (resolverUserId != null) {
                resolverUserId = positive(resolverUserId, "resolverUserId");
            }
            ruleVersion = optionalText(ruleVersion, 64, "ruleVersion");
            targetResultJson = optionalJson(targetResultJson, "targetResultJson");
        } catch (cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException ex) {
            throw corrupted(ex.getMessage());
        }
        boolean closureComplete = resolverUserId != null && ruleVersion != null
                && targetResultJson != null && resolvedAt != null;
        if (status == MigrationIssueStatus.CLOSED && !closureComplete) {
            throw corrupted("CLOSED requires complete resolution fields");
        }
        if (status == MigrationIssueStatus.OPEN
                && (resolverUserId != null || ruleVersion != null || targetResultJson != null || resolvedAt != null)) {
            throw corrupted("OPEN forbids resolution fields");
        }
    }
}
