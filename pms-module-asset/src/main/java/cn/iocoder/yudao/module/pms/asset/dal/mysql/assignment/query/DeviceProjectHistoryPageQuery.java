package cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query;

public record DeviceProjectHistoryPageQuery(
        Long tenantId,
        String deviceSn,
        Long offset,
        Long limit) {
}
