package cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.query;

public record DeviceWarrantyRecordPageQuery(
        Long tenantId,
        String deviceSn,
        Long pageNo,
        Long pageSize) {
}
