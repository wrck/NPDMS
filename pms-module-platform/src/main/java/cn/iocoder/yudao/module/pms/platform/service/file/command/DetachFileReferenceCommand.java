package cn.iocoder.yudao.module.pms.platform.service.file.command;

public record DetachFileReferenceCommand(
        Long tenantId, Long actorUserId, String idempotencyKey,
        Long referenceId, Integer expectedReferenceVersion,
        String ownerContext, String objectType, String objectId,
        String purposeCode, String referenceKey, String reason, tools.jackson.databind.JsonNode ownerExecutionContext) {
    public DetachFileReferenceCommand(Long tenantId, Long actorUserId, String idempotencyKey, Long referenceId, Integer expectedReferenceVersion, String ownerContext, String objectType, String objectId, String purposeCode, String referenceKey, String reason) {
        this(tenantId, actorUserId, idempotencyKey, referenceId, expectedReferenceVersion, ownerContext, objectType, objectId, purposeCode, referenceKey, reason, null);
    }
    @Override public tools.jackson.databind.JsonNode ownerExecutionContext() {
        return ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
    public DetachFileReferenceCommand {
        ownerExecutionContext = ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
}
