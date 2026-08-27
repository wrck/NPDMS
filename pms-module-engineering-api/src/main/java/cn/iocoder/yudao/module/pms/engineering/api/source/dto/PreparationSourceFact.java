package cn.iocoder.yudao.module.pms.engineering.api.source.dto;

public record PreparationSourceFact(
        Long projectId,
        Long itemId,
        String sourceTypeCode,
        String sourceObjectType,
        String sourceObjectId,
        String sourceReferenceKey,
        String normalizedResultCode,
        String sourceFactVersion,
        String sourceWatermark,
        boolean requirementSatisfied) {
}
