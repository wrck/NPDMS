package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

import java.util.List;

public record CutoverActiveDeviceQuery(Long tenantId, Long projectId, List<Long> deviceIds) {
    public CutoverActiveDeviceQuery {
        deviceIds = List.copyOf(deviceIds);
    }
}
