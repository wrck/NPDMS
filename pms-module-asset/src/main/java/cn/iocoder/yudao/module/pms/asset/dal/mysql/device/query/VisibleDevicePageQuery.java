package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Set;

public record VisibleDevicePageQuery(
        Long tenantId,
        Set<Long> visibleProjectIds,
        String sn,
        String productCode,
        Long projectId,
        Long customerId,
        Integer pageNo,
        Integer pageSize) {

    public long offset() {
        return (long) (pageNo - 1) * pageSize;
    }
}
