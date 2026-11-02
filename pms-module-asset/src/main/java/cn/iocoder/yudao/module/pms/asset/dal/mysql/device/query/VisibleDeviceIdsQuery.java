package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.List;
import java.util.Set;

/** 仅按指定设备集合读取当前受权记录；范围由AST使用原Owner接口生成。 */
public record VisibleDeviceIdsQuery(Long tenantId, Set<Long> deviceIds, Long projectId,
        List<DeviceOrganizationGrant> organizationGrants, DevicePageReadScope readScope,
        boolean tenantWideRead) {
    public boolean hasDeviceFilters() { return true; }
}
