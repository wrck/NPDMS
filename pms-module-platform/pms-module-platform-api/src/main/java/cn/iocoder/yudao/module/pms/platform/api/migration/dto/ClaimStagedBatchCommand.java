package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationEvidenceContractRules.*;

public record ClaimStagedBatchCommand(
        Long tenantId,
        String ownerContextCode,
        String purposeCode,
        List<String> sourceSystems,
        List<String> sourceTables,
        String correlationId) {

    public ClaimStagedBatchCommand {
        tenantId = positive(tenantId, "tenantId");
        ownerContextCode = text(ownerContextCode, 32, "ownerContextCode");
        purposeCode = text(purposeCode, 64, "purposeCode");
        sourceSystems = normalizedCodes(sourceSystems, 32, "sourceSystems");
        sourceTables = normalizedCodes(sourceTables, 64, "sourceTables");
        correlationId = text(correlationId, 128, "correlationId");
    }

    private static List<String> normalizedCodes(List<String> values, int maxLength, String field) {
        List<String> normalized = completeList(values, field).stream()
                .map(value -> text(value, maxLength, field))
                .sorted(Comparator.naturalOrder())
                .toList();
        if (normalized.isEmpty() || normalized.stream().distinct().count() != normalized.size()) {
            throw invalid(field + " must be nonempty and unique");
        }
        return normalized;
    }
}
