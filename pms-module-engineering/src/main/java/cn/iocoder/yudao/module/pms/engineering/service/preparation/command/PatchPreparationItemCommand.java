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
        String formValueSnapshot, List<EvidenceReference> evidenceReferences) {

    public record EvidenceReference(Long artifactId, Integer versionNo, String referenceKey,
                                    FileFactVersion fileFactVersion, Long scopeVersion) {}
}
