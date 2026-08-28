package cn.iocoder.yudao.module.pms.engineering.api.source.dto;

public record PreparationSourceFactRevalidationQuery(
        Long projectId,
        Long itemId,
        String sourceTypeCode,
        String sourceObjectType,
        String sourceObjectId,
        String sourceReferenceKey,
        String requiredResultPolicySnapshot,
        String expectedNormalizedResultCode,
        String expectedSourceFactVersion,
        String expectedSourceWatermark) {
}
