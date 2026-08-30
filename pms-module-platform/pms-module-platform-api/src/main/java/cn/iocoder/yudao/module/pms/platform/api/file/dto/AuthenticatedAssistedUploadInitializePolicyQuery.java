package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record AuthenticatedAssistedUploadInitializePolicyQuery(
        Long tenantId, Long actorUserId, Long taskId, Long questionnaireId,
        String requestId, Long responseId, String policyKey,
        String fileSlotKey, Integer fileSequence) {
}
