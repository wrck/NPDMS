package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

public record CustomerDeviceSummaryPageQuery(
        Long tenantId,
        Long customerId,
        Integer pageNo,
        Integer pageSize) {

    public long offset() {
        return (long) (pageNo - 1) * pageSize;
    }
}
