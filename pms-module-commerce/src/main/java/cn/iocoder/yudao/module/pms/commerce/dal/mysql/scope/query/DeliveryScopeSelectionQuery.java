package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.util.List;

/** Current confirmed scope references selected for the project's equipment list. */
public record DeliveryScopeSelectionQuery(Long tenantId, Long projectId, List<Long> scopeIds) {}
