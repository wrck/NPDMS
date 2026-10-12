package cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query;

import java.util.Set;

/** Project scope is resolved by the Owner before querying the device's history. */
public record DeviceSoftwareVersionHistoryPageQuery(Long tenantId, Long deviceId,
                                                   Set<Long> visibleProjectIds, int pageNo, int pageSize) {
}
