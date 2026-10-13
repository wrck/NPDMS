package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query;

import java.util.List;

/** The reviewed existing orders of one tenant; empty identities never widen the query. */
public record OrderFieldCarryQuery(Long tenantId, List<Long> targetIds) {}
