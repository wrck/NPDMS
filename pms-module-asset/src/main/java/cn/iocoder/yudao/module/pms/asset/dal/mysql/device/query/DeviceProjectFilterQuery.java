package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

/** 按设备条件解析归属项目ID的查询；序列号/产品型号包含匹配，维保状态等值。 */
public record DeviceProjectFilterQuery(
        Long tenantId,
        String deviceSnKeyword,
        String deviceProductModelKeyword,
        String deviceWarrantyStatus) {
}
