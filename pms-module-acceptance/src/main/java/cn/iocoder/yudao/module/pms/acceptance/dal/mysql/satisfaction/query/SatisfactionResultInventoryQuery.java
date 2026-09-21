package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query;

import java.util.List;

public record SatisfactionResultInventoryQuery(Long tenantId, Long projectId, List<Long> taskIds,
        Long afterId, int limit, boolean historical) { }
