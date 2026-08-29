package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

import java.util.List;

public record ArchiveFileReferenceSetsCommand(
        String operationId,
        String archiveBatchId,
        String businessDecisionRef,
        Long actorUserId,
        FileReferenceSetKey attachmentSetKey,
        FileReferenceSetKey archiveSetKey,
        Long expectedScopeVersion,
        List<FileArtifactVersionFact> orderedExpectedPublicFileFacts) {

    public ArchiveFileReferenceSetsCommand {
        operationId = FileActionCodes.requireText(operationId, "operationId");
        archiveBatchId = FileActionCodes.requireText(archiveBatchId, "archiveBatchId");
        businessDecisionRef = FileActionCodes.requireText(businessDecisionRef, "businessDecisionRef");
        if (actorUserId == null || actorUserId <= 0 || expectedScopeVersion == null || expectedScopeVersion < 0
                || attachmentSetKey == null || archiveSetKey == null
                || orderedExpectedPublicFileFacts == null || orderedExpectedPublicFileFacts.isEmpty()) {
            throw new IllegalArgumentException("invalid archive reference set command");
        }
        boolean sameObject = attachmentSetKey.ownerContext().equals(archiveSetKey.ownerContext())
                && attachmentSetKey.objectType().equals(archiveSetKey.objectType())
                && attachmentSetKey.objectId().equals(archiveSetKey.objectId());
        boolean acceptanceReport = "ACC".equals(attachmentSetKey.ownerContext())
                && "ACCEPTANCE_REPORT_VERSION".equals(attachmentSetKey.objectType())
                && "ACCEPTANCE_REPORT_ATTACHMENT".equals(attachmentSetKey.purposeCode())
                && "ACCEPTANCE_REPORT_ARCHIVE".equals(archiveSetKey.purposeCode());
        if (!sameObject || !acceptanceReport) {
            throw new IllegalArgumentException("unsupported archive reference set target");
        }
        orderedExpectedPublicFileFacts = List.copyOf(orderedExpectedPublicFileFacts);
    }
}
