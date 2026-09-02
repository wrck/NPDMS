package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record ExternalTargetMapping(
        String targetContext,
        String targetObjectType,
        String targetTable,
        Long targetId,
        String targetRole,
        int targetSequence) {

    public ExternalTargetMapping {
        targetContext = text(targetContext, 32, "targetContext");
        targetObjectType = text(targetObjectType, 64, "targetObjectType");
        targetTable = text(targetTable, 64, "targetTable");
        targetId = positive(targetId, "targetId");
        targetRole = text(targetRole, 32, "targetRole");
        targetSequence = nonNegative(targetSequence, "targetSequence");
    }
}
