package cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query;

public record DynamicFormInstanceValueUpdate(
        Long tenantId, Long instanceId, Integer expectedVersion, String valueJson, String updater) {
}
