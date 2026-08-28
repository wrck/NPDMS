package cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query;

public record DynamicFormTemplateVersionUpdate(
        Long tenantId, Long templateId, Integer expectedVersion,
        boolean updateTemplateName, String templateName,
        boolean updateCategoryCode, String categoryCode,
        boolean updateDescription, String description,
        boolean updateAvailabilityCode, String availabilityCode,
        boolean updateCurrentPublishedRevisionId, Long currentPublishedRevisionId,
        String updater) {
}
