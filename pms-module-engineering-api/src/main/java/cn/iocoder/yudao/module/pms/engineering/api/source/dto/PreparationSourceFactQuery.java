package cn.iocoder.yudao.module.pms.engineering.api.source.dto;

/** tenantId只取受信上下文，不由调用请求传入。 */
public record PreparationSourceFactQuery(
        Long projectId,
        Long itemId,
        String sourceTypeCode,
        String sourceObjectType,
        String sourceObjectId,
        String sourceReferenceKey,
        String requiredResultPolicySnapshot) {
}
