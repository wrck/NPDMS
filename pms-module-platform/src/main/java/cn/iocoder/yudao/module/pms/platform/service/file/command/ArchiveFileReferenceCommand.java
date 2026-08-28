package cn.iocoder.yudao.module.pms.platform.service.file.command;

public record ArchiveFileReferenceCommand(
        Long tenantId, Long actorUserId, String idempotencyKey,
        Long referenceId, Integer expectedReferenceVersion,
        String archiveBatchId, String businessDecisionRef, String archiveNote,
        String ownerContext, String objectType, String objectId,
        String purposeCode, String referenceKey) {
}
