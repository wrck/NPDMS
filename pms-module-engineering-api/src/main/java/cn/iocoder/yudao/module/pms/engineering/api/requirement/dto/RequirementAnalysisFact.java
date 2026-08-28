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
        RequirementAnalysisWorkBindingFact workBindingFact,
        Long dynamicFormTemplateId,
        Long dynamicFormInstanceId,
        Integer dynamicFormInstanceVersion,
        Integer dynamicFormRevisionNo,
        Integer dynamicFormRevisionFactVersion,
        String engineCode,
        String designerVersion,
        String rendererVersion,
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

    public RequirementAnalysisFact(Long projectId, Long preparationId, Integer businessVersion,
                                   String status, Integer contentVersion, Integer projectVersion,
                                   Long templateRevision, Long completedBy, LocalDateTime completedAt,
                                   boolean currentEffective, Long currentEffectivePreparationId,
                                   Integer currentEffectiveBusinessVersion,
                                   List<RequirementAnalysisSectionFact> orderedSectionFacts,
                                   List<RequirementAnalysisFileFact> fileFacts,
                                   RequirementAnalysisFactVector factVector) {
        this(projectId, preparationId, businessVersion, status, contentVersion, projectVersion,
                templateRevision, null, null, null, null, null, null, null, null, null,
                completedBy, completedAt, currentEffective,
                currentEffectivePreparationId, currentEffectiveBusinessVersion,
                orderedSectionFacts, fileFacts, factVector);
    }
}
