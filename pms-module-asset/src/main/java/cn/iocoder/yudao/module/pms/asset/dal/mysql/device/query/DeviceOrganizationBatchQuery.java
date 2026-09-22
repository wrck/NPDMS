package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Set;
public record DeviceOrganizationBatchQuery(Long tenantId, long afterId, int limit, boolean rebuildAll,
        Set<Long> deviceIds, Set<Long> projectIds, Set<String> contractNumbers) {}
