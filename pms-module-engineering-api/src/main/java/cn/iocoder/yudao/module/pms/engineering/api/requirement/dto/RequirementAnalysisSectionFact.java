package cn.iocoder.yudao.module.pms.engineering.api.requirement.dto;

import java.util.List;

public record RequirementAnalysisSectionFact(
        String sectionCode,
        String sectionName,
        String sectionKindCode,
        String fieldTypeCode,
        boolean required,
        Integer sortOrder,
        String schemaSnapshot,
        String valueSnapshot,
        Integer sectionVersion,
        List<RequirementAnalysisFileFact> fileFacts) {

    public RequirementAnalysisSectionFact {
        fileFacts = fileFacts == null ? List.of() : List.copyOf(fileFacts);
    }
}
