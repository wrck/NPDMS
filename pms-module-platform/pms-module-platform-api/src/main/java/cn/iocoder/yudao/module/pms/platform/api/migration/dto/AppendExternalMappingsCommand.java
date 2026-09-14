package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

/** One idempotent command for a bounded page; individual source classifications remain immutable. */
public record AppendExternalMappingsCommand(List<AppendExternalMappingCommand> mappings,
                                             String idempotencyKey, String correlationId) {
    public AppendExternalMappingsCommand {
        mappings = completeList(mappings, "mappings").stream()
                .sorted(Comparator.comparing(AppendExternalMappingCommand::sourceRecordId)).toList();
        if (mappings.isEmpty() || mappings.size() > 1000) throw invalid("mappings size must be 1..1000");
        var first = mappings.getFirst();
        var keys = new HashSet<Long>();
        for (var row : mappings) {
            if (!first.tenantId().equals(row.tenantId()) || !first.batchId().equals(row.batchId())
                    || !keys.add(row.sourceRecordId())) throw invalid("mapping page must contain distinct sources in one batch");
        }
        idempotencyKey = text(idempotencyKey, 128, "idempotencyKey");
        correlationId = text(correlationId, 128, "correlationId");
    }
    public Long tenantId() { return mappings.getFirst().tenantId(); }
    public Long batchId() { return mappings.getFirst().batchId(); }
}
