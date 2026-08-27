package cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query;

public record DynamicFormTemplatePageQuery(
        Long tenantId, String templateNameKeyword, String categoryCode,
        String availabilityCode, boolean selectionOnly, long offset, int limit) {
}
