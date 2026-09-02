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
        boolean satisfactionArchive = "ACC".equals(attachmentSetKey.ownerContext())
                && "ACC".equals(archiveSetKey.ownerContext())
                && "SATISFACTION_RESULT".equals(archiveSetKey.objectType())
                && "SATISFACTION_ARCHIVE".equals(archiveSetKey.purposeCode())
                && (("SATISFACTION_RESULT".equals(attachmentSetKey.objectType())
                    && attachmentSetKey.objectId().equals(archiveSetKey.objectId())
                    && "SATISFACTION_RESULT_DOCUMENT".equals(attachmentSetKey.purposeCode()))
                    || ("SATISFACTION_RESPONSE".equals(attachmentSetKey.objectType())
                    && ("SATISFACTION_SIGNATURE".equals(attachmentSetKey.purposeCode())
                    || "SATISFACTION_ATTACHMENT".equals(attachmentSetKey.purposeCode()))));
        if (!(sameObject && acceptanceReport) && !satisfactionArchive) {
            throw new IllegalArgumentException("unsupported archive reference set target");
        }
        orderedExpectedPublicFileFacts = List.copyOf(orderedExpectedPublicFileFacts);
    }
}
