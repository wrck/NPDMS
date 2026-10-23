package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Set;

public record ShippedProjectQuery(Long tenantId, Set<Long> visibleProjectIds) {}
