package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record BusinessGrantUploadCompletePolicyQuery(
        Long tenantId, Long grantId, Integer grantVersion, Long questionnaireId,
        String requestId, Long responseId, String policyKey,
        String fileSlotKey, Integer fileSequence, Long expectedScopeVersion) {
}
