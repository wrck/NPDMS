package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query;

import java.util.List;

/** Frozen existing line identities; an empty list returns no rows. */
public record OrderLineFieldCarryQuery(Long tenantId, List<Long> targetIds) {}
