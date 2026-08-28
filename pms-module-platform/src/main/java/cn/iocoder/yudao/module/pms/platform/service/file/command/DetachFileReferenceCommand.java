package cn.iocoder.yudao.module.pms.platform.service.file.command;

public record DetachFileReferenceCommand(
        Long tenantId, Long actorUserId, String idempotencyKey,
        Long referenceId, Integer expectedReferenceVersion,
        String ownerContext, String objectType, String objectId,
        String purposeCode, String referenceKey, String reason) {
}
