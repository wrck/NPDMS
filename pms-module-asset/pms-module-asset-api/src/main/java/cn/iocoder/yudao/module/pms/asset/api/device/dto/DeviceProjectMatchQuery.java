package cn.iocoder.yudao.module.pms.asset.api.device.dto;

/** 项目列表按设备条件过滤的解析查询；序列号/产品型号为包含匹配，维保状态为CRM同步原文等值。 */
public record DeviceProjectMatchQuery(
        Long tenantId,
        String deviceSnKeyword,
        String deviceProductModelKeyword,
        String deviceWarrantyStatus) {
}
