package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record AppendExternalMappingCommand(
        Long tenantId,
        Long batchId,
        Long sourceRecordId,
        SourceReconciliationType resultType,
        List<ExternalTargetMapping> targets,
        String idempotencyKey,
        String correlationId) {

    private static final Comparator<ExternalTargetMapping> STABLE_ORDER = Comparator
            .comparing(ExternalTargetMapping::targetRole)
            .thenComparingInt(ExternalTargetMapping::targetSequence)
            .thenComparing(ExternalTargetMapping::targetContext)
            .thenComparing(ExternalTargetMapping::targetObjectType)
            .thenComparing(ExternalTargetMapping::targetId);

    public AppendExternalMappingCommand {
        tenantId = positive(tenantId, "tenantId");
        batchId = positive(batchId, "batchId");
        sourceRecordId = positive(sourceRecordId, "sourceRecordId");
        if (resultType == null) {
            throw invalid("resultType must not be null");
        }
        targets = completeList(targets, "targets").stream().sorted(STABLE_ORDER).toList();
        if (resultType == SourceReconciliationType.MAPPED && targets.isEmpty()) {
            throw invalid("MAPPED requires at least one target");
        }
        if (resultType == SourceReconciliationType.RETAINED && !targets.isEmpty()) {
            throw invalid("RETAINED forbids targets");
        }
        Set<String> positions = new HashSet<>();
        for (ExternalTargetMapping target : targets) {
            if (!positions.add(target.targetRole() + "\u0000" + target.targetSequence())) {
                throw invalid("duplicate targetRole and targetSequence");
            }
        }
        idempotencyKey = text(idempotencyKey, 128, "idempotencyKey");
        correlationId = text(correlationId, 128, "correlationId");
    }
}
