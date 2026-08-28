package cn.iocoder.yudao.module.pms.engineering.api.requirement.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RequirementAnalysisFactVector(
        Long projectId,
        Integer projectVersion,
        RequirementAnalysisWorkBindingFact workBindingFact,
        Long preparationId,
        Integer businessVersion,
        Integer contentVersion,
        Long templateRevision,
        Long dynamicFormTemplateId,
        Long dynamicFormTemplateRevisionId,
        Integer dynamicFormRevisionNo,
        Integer dynamicFormRevisionFactVersion,
        Long dynamicFormInstanceId,
        Integer dynamicFormInstanceVersion,
        String engineCode,
        String designerVersion,
        String rendererVersion,
        LocalDateTime completedAt,
        boolean currentEffective,
        Long currentEffectivePreparationId,
        Integer currentEffectiveBusinessVersion,
        List<RequirementAnalysisSectionFact> orderedSectionFacts) {

    public RequirementAnalysisFactVector {
        orderedSectionFacts = orderedSectionFacts == null ? List.of() : List.copyOf(orderedSectionFacts);
    }

}
