package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Set;

/** IDs have already passed the calling current-device query's authorization. */
public record DeviceCurrentModelQuery(Long tenantId, Set<Long> deviceIds) {}
