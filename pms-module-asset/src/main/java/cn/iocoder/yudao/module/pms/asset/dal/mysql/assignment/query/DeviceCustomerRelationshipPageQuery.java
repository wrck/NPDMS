package cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query;

public record DeviceCustomerRelationshipPageQuery(
        Long tenantId,
        String deviceSn,
        Long offset,
        Long limit) {
}
