package cn.iocoder.yudao.module.pms.engineering.service.preparation.command;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;

import java.util.List;
import java.util.Set;

public record PatchPreparationItemCommand(Long preparationId, Long itemId,
        Integer expectedItemVersion, Integer expectedPreparationVersion,
        Integer expectedInputVersion, Integer expectedReadinessVersion,
        Integer expectedFormVersion, Integer expectedProjectVersion,
        Set<String> submittedFields, String applicabilityCode, Boolean outsourced,
        Long assigneeUserId, String notApplicableReason, String siteResultCode, String siteResultDetail,
        String formValueSnapshot, List<EvidenceReference> evidenceReferences,
        cn.iocoder.yudao.module.pms.engineering.domain.preparation.PreparationSurveyResult surveyResult) {

    // Preserve internal callers of the explicit legacy form path.
    public PatchPreparationItemCommand(Long preparationId, Long itemId,
            Integer expectedItemVersion, Integer expectedPreparationVersion,
            Integer expectedInputVersion, Integer expectedReadinessVersion,
            Integer expectedFormVersion, Integer expectedProjectVersion,
            Set<String> submittedFields, String applicabilityCode, Boolean outsourced,
            Long assigneeUserId, String notApplicableReason, String siteResultCode, String siteResultDetail,
            String formValueSnapshot, List<EvidenceReference> evidenceReferences) {
        this(preparationId, itemId, expectedItemVersion, expectedPreparationVersion,
                expectedInputVersion, expectedReadinessVersion, expectedFormVersion, expectedProjectVersion,
                submittedFields, applicabilityCode, outsourced, assigneeUserId, notApplicableReason,
                siteResultCode, siteResultDetail, formValueSnapshot, evidenceReferences, null);
    }

    public record EvidenceReference(Long artifactId, Integer versionNo, String referenceKey,
                                    FileFactVersion fileFactVersion, Long scopeVersion) {}
}
