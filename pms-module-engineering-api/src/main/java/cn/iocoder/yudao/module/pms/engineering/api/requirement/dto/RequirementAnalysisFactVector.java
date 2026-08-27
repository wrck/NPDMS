package cn.iocoder.yudao.module.pms.engineering.api.requirement.dto;

import java.util.List;

public record RequirementAnalysisFactVector(
        Long preparationId,
        Integer businessVersion,
        Integer contentVersion,
        Long templateRevision,
        List<RequirementAnalysisSectionFact> orderedSectionFacts) {

    public RequirementAnalysisFactVector {
        orderedSectionFacts = orderedSectionFacts == null ? List.of() : List.copyOf(orderedSectionFacts);
    }
}
