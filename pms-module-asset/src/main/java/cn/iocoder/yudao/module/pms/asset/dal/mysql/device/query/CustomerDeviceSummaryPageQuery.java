package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Set;

public record CustomerDeviceSummaryPageQuery(
        Long tenantId,
        Long customerId,
        Set<Long> visibleProjectIds,
        Integer pageNo,
        Integer pageSize) {

    public long offset() {
        return (long) (pageNo - 1) * pageSize;
    }
}
