package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record AuthenticatedAssistedUploadPolicyFact(
        Long taskId, Long questionnaireId, String requestId, Long responseId,
        String policyKey, String fileSlotKey, Integer fileSequence,
        Long actorUserId, Long scopeVersion, FileBusinessObjectPolicyFact filePolicy) {
}
