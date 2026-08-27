package cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query;

public record DynamicFormInstancePageQuery(
        Long tenantId, String instanceNameKeyword, Long createdBy, long offset, int limit) {
}
