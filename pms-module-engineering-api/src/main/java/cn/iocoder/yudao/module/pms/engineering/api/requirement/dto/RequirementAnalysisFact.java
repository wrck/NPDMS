package cn.iocoder.yudao.module.pms.engineering.api.requirement.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RequirementAnalysisFact(
        Long projectId,
        Long preparationId,
        Integer businessVersion,
        String status,
        Integer contentVersion,
        Integer projectVersion,
        Long templateRevision,
        Long completedBy,
        LocalDateTime completedAt,
        boolean currentEffective,
        Long currentEffectivePreparationId,
        Integer currentEffectiveBusinessVersion,
        List<RequirementAnalysisSectionFact> orderedSectionFacts,
        List<RequirementAnalysisFileFact> fileFacts,
        RequirementAnalysisFactVector factVector) {

    public RequirementAnalysisFact {
        orderedSectionFacts = orderedSectionFacts == null ? List.of() : List.copyOf(orderedSectionFacts);
        fileFacts = fileFacts == null ? List.of() : List.copyOf(fileFacts);
    }
}
