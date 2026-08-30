package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record BusinessGrantUploadPolicyFact(
        Long grantId, Integer grantVersion, Long questionnaireId,
        String requestId, Long responseId, String policyKey,
        String fileSlotKey, Integer fileSequence, Long grantIssuerUserId,
        Long scopeVersion, FileBusinessObjectPolicyFact filePolicy) {
}
