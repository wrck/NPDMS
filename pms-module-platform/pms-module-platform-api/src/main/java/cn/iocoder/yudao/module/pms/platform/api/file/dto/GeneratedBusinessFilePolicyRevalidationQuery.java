package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record GeneratedBusinessFilePolicyRevalidationQuery(
        Long tenantId, Long actorUserId, Long resultId, Long collectionTaskId,
        Long questionnaireId, Long responseId, Integer expectedTaskVersion,
        String ownerContext, String objectType, String purposeCode,
        String referenceKey, String requiredAction, Long expectedScopeVersion) {
}
